package com.fabricmanagement.production.execution.batch.api.query;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.app.BatchCommitmentQuantityService;
import com.fabricmanagement.production.execution.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitSoftHoldRepository;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public production read contract for sales lot/piece selection. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductionSalesLotQueryService {

  private static final Set<BatchStatus> SALEABLE_BATCH_STATUSES =
      Set.of(BatchStatus.AVAILABLE, BatchStatus.RESERVED);
  private static final Set<StockUnitStatus> SELECTABLE_PIECE_STATUSES =
      Set.of(StockUnitStatus.AVAILABLE, StockUnitStatus.PARTIAL);
  private final BatchRepository batchRepository;
  private final BatchLotQuantityIntentRepository lotIntentRepository;
  private final StockUnitRepository stockUnitRepository;
  private final StockUnitSoftHoldRepository softHoldRepository;
  private final QualityGradeQueryService qualityGradeQueryService;
  private final BatchPrimaryMeasureService primaryMeasureService;
  private final BatchCommitmentQuantityService commitmentQuantityService;

  public List<ProductionSalesLotReference> listSaleableLots() {
    return listSaleableLots(null);
  }

  public List<ProductionSalesLotReference> listSaleableLots(UUID excludedQuoteLineId) {
    UUID tenantId = TenantContext.requireTenantId();
    List<Batch> batches =
        batchRepository.findByTenantIdAndStatusIn(tenantId, SALEABLE_BATCH_STATUSES).stream()
            .filter(batch -> Boolean.TRUE.equals(batch.getIsActive()))
            .sorted(
                Comparator.comparing(Batch::getBatchCode, Comparator.nullsLast(String::compareTo)))
            .toList();
    return toReferences(tenantId, batches, excludedQuoteLineId);
  }

  public List<ProductionSalesLotReference> findLotsByIds(Collection<UUID> lotIds) {
    if (lotIds == null || lotIds.isEmpty()) {
      return List.of();
    }
    UUID tenantId = TenantContext.requireTenantId();
    List<Batch> batches =
        batchRepository.findAllById(lotIds).stream()
            .filter(batch -> tenantId.equals(batch.getTenantId()))
            .filter(batch -> Boolean.TRUE.equals(batch.getIsActive()))
            .toList();
    return toReferences(tenantId, batches, null);
  }

  private List<ProductionSalesLotReference> toReferences(
      UUID tenantId, List<Batch> batches, UUID excludedQuoteLineId) {
    if (batches.isEmpty()) {
      return List.of();
    }
    List<UUID> batchIds = batches.stream().map(Batch::getId).toList();
    Map<UUID, List<StockUnit>> unitsByBatch =
        stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(tenantId, batchIds).stream()
            .collect(Collectors.groupingBy(StockUnit::getBatchId));
    Map<UUID, Long> softHoldCounts =
        softHoldRepository.countActiveByStockUnitIds(
            tenantId,
            unitsByBatch.values().stream()
                .flatMap(Collection::stream)
                .map(StockUnit::getId)
                .toList());
    Map<UUID, BatchCommitmentQuantityService.Summary> commitments =
        commitmentQuantityService.summarize(tenantId, batches, excludedQuoteLineId);
    Map<UUID, List<ProductionSalesLotIntentReference>> intentsByBatch =
        lotIntentRepository.findActiveByBatchIds(tenantId, batchIds, excludedQuoteLineId).stream()
            .map(BatchLotIntentView::from)
            .collect(
                Collectors.groupingBy(
                    BatchLotIntentView::batchId,
                    Collectors.mapping(BatchLotIntentView::intent, Collectors.toList())));
    Map<UUID, LotColourReference> coloursByBatch = resolveColoursByBatch(tenantId, batchIds);

    return batches.stream()
        .map(
            batch -> {
              List<StockUnit> units =
                  unitsByBatch.getOrDefault(batch.getId(), List.of()).stream()
                      .sorted(Comparator.comparing(StockUnit::getBarcode))
                      .toList();
              var resolution = primaryMeasureService.resolve(batch);
              PrimaryMeasure primaryMeasure = resolution.primaryMeasure();
              LotQualityReference quality = resolveLotQuality(units).orElse(null);
              LotColourReference colour = coloursByBatch.get(batch.getId());
              List<ProductionSalesPieceReference> pieces =
                  units.stream()
                      .map(
                          unit ->
                              toPiece(
                                  unit,
                                  primaryMeasure,
                                  softHoldCounts.getOrDefault(unit.getId(), 0L)))
                      .toList();
              BigDecimal physicalQuantity =
                  pieces.isEmpty() ? physicalBatchQuantity(batch) : sumSelectablePieces(pieces);
              var commitment = commitments.get(batch.getId());
              BigDecimal softIntentQuantity = commitment.softIntent();
              BigDecimal hardReservedQuantity = commitment.hardReserved();
              BigDecimal freeQuantity =
                  physicalQuantity.subtract(softIntentQuantity).subtract(hardReservedQuantity);
              boolean overCommitted = freeQuantity.compareTo(BigDecimal.ZERO) < 0;
              if (overCommitted) {
                log.warn(
                    "Over-committed sales lot: tenantId={}, batchId={}, deficit={}",
                    tenantId,
                    batch.getId(),
                    freeQuantity.abs());
                freeQuantity = BigDecimal.ZERO;
              }
              return new ProductionSalesLotReference(
                  batch.getId(),
                  batch.getBatchCode(),
                  batch.getStatus().name(),
                  SALEABLE_BATCH_STATUSES.contains(batch.getStatus()),
                  primaryMeasure.name(),
                  resolution.primaryUnit(),
                  quality,
                  colour,
                  pieces.isEmpty()
                      ? canonicalBatchQuantity(batch, batch.getAvailableQuantity(), primaryMeasure)
                      : sumSelectablePieces(pieces),
                  physicalQuantity,
                  softIntentQuantity,
                  hardReservedQuantity,
                  freeQuantity,
                  overCommitted,
                  intentsByBatch.getOrDefault(batch.getId(), List.of()),
                  pieces);
            })
        .toList();
  }

  private ProductionSalesPieceReference toPiece(
      StockUnit unit, PrimaryMeasure primaryMeasure, long softReservedCount) {
    BigDecimal primaryValue =
        primaryMeasure == PrimaryMeasure.LENGTH && unit.getLength() != null
            ? primaryMeasureService
                .toCanonical(unit.getLength(), unit.getLengthUnit(), PrimaryMeasure.LENGTH)
                .orElse(null)
            : primaryMeasureService
                .toCanonical(unit.getCurrentWeight(), unit.getUnit(), PrimaryMeasure.WEIGHT)
                .orElse(null);
    String primaryUnit = primaryMeasureService.canonicalUnit(primaryMeasure);
    return new ProductionSalesPieceReference(
        unit.getId(),
        unit.getBarcode(),
        unit.getPackageType().name(),
        unit.getLength(),
        unit.getLengthUnit(),
        unit.getCurrentWeight(),
        unit.getUnit(),
        primaryValue,
        primaryUnit,
        unit.getStatus().name(),
        SELECTABLE_PIECE_STATUSES.contains(unit.getStatus()),
        softReservedCount,
        unit.getQualityGradeId());
  }

  private BigDecimal sumSelectablePieces(List<ProductionSalesPieceReference> pieces) {
    return pieces.stream()
        .filter(ProductionSalesPieceReference::selectable)
        .map(ProductionSalesPieceReference::primaryMeasureValue)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal physicalBatchQuantity(Batch batch) {
    PrimaryMeasure measure = primaryMeasureService.resolve(batch).primaryMeasure();
    return canonicalBatchQuantity(
        batch, batch.getQuantity().subtract(batch.getConsumedQuantity()), measure);
  }

  private BigDecimal canonicalBatchQuantity(
      Batch batch, BigDecimal quantity, PrimaryMeasure measure) {
    return primaryMeasureService
        .toCanonical(quantity, batch.getUnit(), measure)
        .orElse(BigDecimal.ZERO);
  }

  private Optional<LotQualityReference> resolveLotQuality(List<StockUnit> units) {
    return units.stream()
        .map(StockUnit::getQualityGradeId)
        .filter(Objects::nonNull)
        .findFirst()
        .flatMap(qualityGradeQueryService::findReferenceById)
        .map(
            ref ->
                new LotQualityReference(
                    ref.id(), ref.code(), ref.name(), ref.saleable(), ref.active()));
  }

  private Map<UUID, LotColourReference> resolveColoursByBatch(UUID tenantId, List<UUID> batchIds) {
    return batchRepository.findColorReferencesByBatchIds(tenantId, batchIds).stream()
        .collect(
            Collectors.toMap(
                BatchRepository.BatchColorProjection::getBatchId,
                projection ->
                    new LotColourReference(
                        projection.getColorId(),
                        projection.getColorCode(),
                        projection.getColorName(),
                        projection.getColorHex(),
                        null)));
  }

  public record ProductionSalesLotReference(
      UUID id,
      String lotNo,
      String status,
      boolean saleable,
      String primaryMeasure,
      String unit,
      LotQualityReference quality,
      LotColourReference colour,
      BigDecimal availableQuantity,
      BigDecimal physicalQuantity,
      BigDecimal softIntentQuantity,
      BigDecimal hardReservedQuantity,
      BigDecimal freeQuantity,
      boolean overCommitted,
      List<ProductionSalesLotIntentReference> intents,
      List<ProductionSalesPieceReference> pieces) {}

  public record ProductionSalesLotIntentReference(
      UUID quoteId,
      String quoteNumber,
      String marketerName,
      BigDecimal quantity,
      LocalDate expiresAt) {}

  public record ProductionSalesPieceReference(
      UUID id,
      String pieceNo,
      String packageType,
      BigDecimal length,
      String lengthUnit,
      BigDecimal weight,
      String weightUnit,
      BigDecimal primaryMeasureValue,
      String primaryMeasureUnit,
      String status,
      boolean selectable,
      long softReservedCount,
      UUID qualityGradeId) {}

  public record LotQualityReference(
      UUID id, String code, String name, boolean saleable, boolean active) {}

  public record LotColourReference(
      UUID id, String code, String name, String colorHex, String colourLabel) {}

  private record BatchLotIntentView(UUID batchId, ProductionSalesLotIntentReference intent) {
    private static BatchLotIntentView from(
        com.fabricmanagement.production.execution.batch.domain.BatchLotQuantityIntent source) {
      return new BatchLotIntentView(
          source.getBatchId(),
          new ProductionSalesLotIntentReference(
              source.getQuoteId(),
              source.getQuoteNumber(),
              source.getMarketerName(),
              source.getQuantity(),
              source.getExpiresAt()));
    }
  }
}
