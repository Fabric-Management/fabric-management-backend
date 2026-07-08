package com.fabricmanagement.production.execution.batch.api.query;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchAttribute;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchAttributeRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitSoftHoldRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import com.fabricmanagement.production.masterdata.color.api.query.ColorQueryService;
import com.fabricmanagement.production.masterdata.color.api.query.ColorQueryService.ColorReference;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public production read contract for sales lot/piece selection. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionSalesLotQueryService {

  private static final Set<BatchStatus> SALEABLE_BATCH_STATUSES =
      Set.of(BatchStatus.AVAILABLE, BatchStatus.RESERVED);
  private static final Set<StockUnitStatus> SELECTABLE_PIECE_STATUSES =
      Set.of(StockUnitStatus.AVAILABLE, StockUnitStatus.PARTIAL);
  private static final Set<String> COLOUR_ATTRIBUTE_CODES =
      Set.of("COLOR", "COLOUR", "COLOR_ID", "COLOUR_ID", "SHADE");

  private final BatchRepository batchRepository;
  private final BatchReservationRepository reservationRepository;
  private final BatchLotQuantityIntentRepository lotIntentRepository;
  private final StockUnitRepository stockUnitRepository;
  private final StockUnitSoftHoldRepository softHoldRepository;
  private final BatchAttributeRepository batchAttributeRepository;
  private final QualityGradeQueryService qualityGradeQueryService;
  private final ColorQueryService colorQueryService;
  private final WorkOrderRepository workOrderRepository;
  private final PrimaryMeasureResolver primaryMeasureResolver;

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
    Map<UUID, BigDecimal> softIntentQuantities =
        lotIntentRepository.sumActiveByBatchIds(tenantId, batchIds, excludedQuoteLineId);
    Map<UUID, BigDecimal> hardReservedQuantities =
        reservationRepository.sumActiveRemainingByBatchIds(tenantId, batchIds);
    Map<UUID, List<ProductionSalesLotIntentReference>> intentsByBatch =
        lotIntentRepository.findActiveByBatchIds(tenantId, batchIds, excludedQuoteLineId).stream()
            .map(BatchLotIntentView::from)
            .collect(
                Collectors.groupingBy(
                    BatchLotIntentView::batchId,
                    Collectors.mapping(BatchLotIntentView::intent, Collectors.toList())));
    Map<UUID, LotColourReference> coloursByBatch = resolveColoursByBatch(batchIds);

    return batches.stream()
        .map(
            batch -> {
              List<StockUnit> units =
                  unitsByBatch.getOrDefault(batch.getId(), List.of()).stream()
                      .sorted(Comparator.comparing(StockUnit::getBarcode))
                      .toList();
              PrimaryMeasure primaryMeasure =
                  primaryMeasureResolver.resolve(
                      resolveProcessType(tenantId, batch), batch.getProductType());
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
                  pieces.isEmpty()
                      ? physicalBatchQuantity(batch)
                      : sumSelectablePieces(pieces, primaryMeasure);
              BigDecimal softIntentQuantity =
                  softIntentQuantities.getOrDefault(batch.getId(), BigDecimal.ZERO);
              BigDecimal hardReservedQuantity =
                  hardReservedQuantities.getOrDefault(batch.getId(), BigDecimal.ZERO);
              BigDecimal freeQuantity =
                  physicalQuantity.subtract(softIntentQuantity).subtract(hardReservedQuantity);
              if (freeQuantity.compareTo(BigDecimal.ZERO) < 0) {
                freeQuantity = BigDecimal.ZERO;
              }
              return new ProductionSalesLotReference(
                  batch.getId(),
                  batch.getBatchCode(),
                  batch.getStatus().name(),
                  SALEABLE_BATCH_STATUSES.contains(batch.getStatus()),
                  primaryMeasure.name(),
                  resolveUnit(batch, pieces, primaryMeasure),
                  quality,
                  colour,
                  pieces.isEmpty()
                      ? batch.getAvailableQuantity()
                      : sumSelectablePieces(pieces, primaryMeasure),
                  physicalQuantity,
                  softIntentQuantity,
                  hardReservedQuantity,
                  freeQuantity,
                  intentsByBatch.getOrDefault(batch.getId(), List.of()),
                  pieces);
            })
        .toList();
  }

  private ProductionSalesPieceReference toPiece(
      StockUnit unit, PrimaryMeasure primaryMeasure, long softReservedCount) {
    BigDecimal primaryValue =
        primaryMeasure == PrimaryMeasure.LENGTH && unit.getLength() != null
            ? unit.getLength()
            : unit.getCurrentWeight();
    String primaryUnit =
        primaryMeasure == PrimaryMeasure.LENGTH && unit.getLengthUnit() != null
            ? unit.getLengthUnit()
            : unit.getUnit();
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

  private String resolveProcessType(UUID tenantId, Batch batch) {
    if (batch.getSourceType() == null) {
      return "UNSPECIFIED";
    }
    if (batch.getSourceType() == BatchSourceType.INTERNAL_PRODUCTION
        && batch.getSourceId() != null) {
      return workOrderRepository
          .findByIdAndTenantIdAndIsActiveTrue(batch.getSourceId(), tenantId)
          .map(workOrder -> workOrder.getModuleType().name())
          .orElse(batch.getSourceType().name());
    }
    return batch.getSourceType().name();
  }

  private String resolveUnit(
      Batch batch, List<ProductionSalesPieceReference> pieces, PrimaryMeasure primaryMeasure) {
    if (primaryMeasure == PrimaryMeasure.LENGTH) {
      return pieces.stream()
          .map(ProductionSalesPieceReference::lengthUnit)
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(batch.getUnit());
    }
    return batch.getUnit();
  }

  private BigDecimal sumSelectablePieces(
      List<ProductionSalesPieceReference> pieces, PrimaryMeasure primaryMeasure) {
    return pieces.stream()
        .filter(ProductionSalesPieceReference::selectable)
        .map(piece -> primaryMeasure == PrimaryMeasure.LENGTH ? piece.length() : piece.weight())
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal physicalBatchQuantity(Batch batch) {
    return batch.getQuantity().subtract(batch.getConsumedQuantity());
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

  private Map<UUID, LotColourReference> resolveColoursByBatch(List<UUID> batchIds) {
    return batchAttributeRepository
        .findActiveColourAttributesByBatchIds(batchIds, COLOUR_ATTRIBUTE_CODES)
        .stream()
        .map(attr -> new BatchColour(attr.getBatch().getId(), toColourReference(attr)))
        .filter(entry -> entry.colour() != null)
        .collect(
            Collectors.toMap(BatchColour::batchId, BatchColour::colour, (left, right) -> left));
  }

  private LotColourReference toColourReference(BatchAttribute attribute) {
    String rawValue = attribute.getValue();
    if (rawValue == null || rawValue.isBlank()) {
      return null;
    }
    String trimmed = rawValue.trim();
    Optional<ColorReference> resolved = resolveColourCard(trimmed);
    return resolved
        .map(ref -> new LotColourReference(ref.id(), ref.code(), ref.name(), ref.colorHex(), null))
        .orElseGet(() -> new LotColourReference(null, null, null, null, trimmed));
  }

  private Optional<ColorReference> resolveColourCard(String rawValue) {
    try {
      return colorQueryService.findReferenceById(UUID.fromString(rawValue));
    } catch (IllegalArgumentException ignored) {
      String code = rawValue.toUpperCase(Locale.ROOT);
      return colorQueryService.findReferenceByCode(code);
    }
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

  private record BatchColour(UUID batchId, LotColourReference colour) {}

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
