package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.app.BatchCommitmentQuantityService.UnitMismatch;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchUnitMismatchSource;
import com.fabricmanagement.production.execution.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.execution.batch.domain.exception.StockAvailabilityFilterException;
import com.fabricmanagement.production.execution.batch.dto.StockAvailabilityDtos;
import com.fabricmanagement.production.execution.batch.dto.StockAvailabilityDtos.PhysicalSource;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.StockAvailabilityBatchRepository.Filter;
import com.fabricmanagement.production.execution.batch.infra.repository.StockAvailabilityBatchRepository.ProductRow;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped stock-availability read model for product summaries and lot detail. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StockAvailabilityQueryService {

  private static final Set<StockUnitStatus> SELECTABLE_PIECE_STATUSES =
      Set.of(StockUnitStatus.AVAILABLE, StockUnitStatus.PARTIAL);

  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;
  private final BatchPrimaryMeasureService primaryMeasureService;
  private final BatchCommitmentQuantityService commitmentQuantityService;
  private final QualityGradeQueryService qualityGradeQueryService;

  public Page<StockAvailabilityDtos.Summary> summary(
      UUID colorId,
      Boolean colourless,
      UUID productId,
      UUID batchId,
      UUID qualityGradeId,
      Boolean qualityUnassigned,
      Pageable pageable) {
    Filter filter =
        validatedFilter(colorId, colourless, productId, batchId, qualityGradeId, qualityUnassigned);
    UUID tenantId = TenantContext.requireTenantId();
    Pageable deterministic = pageOnly(pageable);
    Page<ProductRow> products =
        batchRepository.findAvailabilityProducts(tenantId, filter, deterministic);
    if (products.isEmpty()) {
      return new PageImpl<>(List.of(), deterministic, products.getTotalElements());
    }

    List<UUID> productIds = products.stream().map(ProductRow::productId).toList();
    List<Batch> batches =
        batchRepository.findAvailabilityBatchesForProducts(tenantId, filter, productIds);
    Map<UUID, LotComputation> lots = computeLots(tenantId, batches, filter, false);
    Map<UUID, List<LotComputation>> lotsByProduct =
        lots.values().stream().collect(Collectors.groupingBy(LotComputation::productId));

    List<StockAvailabilityDtos.Summary> content =
        products.stream()
            .map(
                product ->
                    toSummary(product, lotsByProduct.getOrDefault(product.productId(), List.of())))
            .toList();
    return new PageImpl<>(content, deterministic, products.getTotalElements());
  }

  public Page<StockAvailabilityDtos.Lot> lots(
      UUID colorId,
      Boolean colourless,
      UUID productId,
      UUID batchId,
      UUID qualityGradeId,
      Boolean qualityUnassigned,
      Pageable pageable) {
    Filter filter =
        validatedFilter(colorId, colourless, productId, batchId, qualityGradeId, qualityUnassigned);
    UUID tenantId = TenantContext.requireTenantId();
    Pageable deterministic = pageOnly(pageable);
    Page<Batch> batches = batchRepository.findAvailabilityLots(tenantId, filter, deterministic);
    if (batches.isEmpty()) {
      return new PageImpl<>(List.of(), deterministic, batches.getTotalElements());
    }
    Map<UUID, LotComputation> computed = computeLots(tenantId, batches.getContent(), filter, true);
    List<StockAvailabilityDtos.Lot> content =
        batches.stream().map(batch -> computed.get(batch.getId()).lot()).toList();
    return new PageImpl<>(content, deterministic, batches.getTotalElements());
  }

  private Filter validatedFilter(
      UUID colorId,
      Boolean colourless,
      UUID productId,
      UUID batchId,
      UUID qualityGradeId,
      Boolean qualityUnassigned) {
    boolean colourlessEnabled = Boolean.TRUE.equals(colourless);
    boolean qualityUnassignedEnabled = Boolean.TRUE.equals(qualityUnassigned);
    if (colorId != null && colourlessEnabled) {
      throw new StockAvailabilityFilterException(
          "colorId and colourless=true are mutually exclusive");
    }
    if (qualityGradeId != null && qualityUnassignedEnabled) {
      throw new StockAvailabilityFilterException(
          "qualityGradeId and qualityUnassigned=true are mutually exclusive");
    }
    if (colorId == null
        && !colourlessEnabled
        && productId == null
        && batchId == null
        && qualityGradeId == null
        && !qualityUnassignedEnabled) {
      throw new StockAvailabilityFilterException(
          "At least one stock availability filter is required");
    }
    return new Filter(
        colorId, colourlessEnabled, productId, batchId, qualityGradeId, qualityUnassignedEnabled);
  }

  private Pageable pageOnly(Pageable pageable) {
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
  }

  private Map<UUID, LotComputation> computeLots(
      UUID tenantId, List<Batch> batches, Filter filter, boolean includeColours) {
    if (batches.isEmpty()) {
      return Map.of();
    }
    List<UUID> batchIds = batches.stream().map(Batch::getId).toList();
    Set<UUID> pieceBackedBatchIds =
        stockUnitRepository.findBatchIdsWithActiveStockUnits(tenantId, batchIds);
    Map<UUID, List<StockUnitRepository.AvailabilityVectorRow>> vectorRows =
        stockUnitRepository.findAvailabilityVectorRows(tenantId, batchIds).stream()
            .collect(Collectors.groupingBy(StockUnitRepository.AvailabilityVectorRow::getBatchId));
    Map<UUID, List<StockUnitRepository.AvailabilityPieceBreakdownRow>> pieceRows =
        stockUnitRepository
            .findAvailabilityPieceBreakdownRows(
                tenantId,
                batchIds,
                SELECTABLE_PIECE_STATUSES,
                filter.qualityGradeId(),
                filter.qualityUnassigned())
            .stream()
            .collect(
                Collectors.groupingBy(
                    StockUnitRepository.AvailabilityPieceBreakdownRow::getBatchId));
    List<StockUnitRepository.AvailabilityQualityBreakdownRow> allQualityRows =
        stockUnitRepository.findAvailabilityQualityBreakdownRows(
            tenantId,
            batchIds,
            SELECTABLE_PIECE_STATUSES,
            filter.qualityGradeId(),
            filter.qualityUnassigned());
    Map<UUID, List<StockUnitRepository.AvailabilityQualityBreakdownRow>> qualityRows =
        allQualityRows.stream()
            .collect(
                Collectors.groupingBy(
                    StockUnitRepository.AvailabilityQualityBreakdownRow::getBatchId));
    Set<UUID> qualityGradeIds =
        allQualityRows.stream()
            .map(StockUnitRepository.AvailabilityQualityBreakdownRow::getQualityGradeId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, QualityGradeReference> qualityGrades =
        qualityGradeQueryService.findReferencesByIds(qualityGradeIds).stream()
            .collect(Collectors.toMap(QualityGradeReference::id, Function.identity()));
    Map<UUID, BatchCommitmentQuantityService.Summary> commitments =
        commitmentQuantityService.summarize(tenantId, batches, null);
    Map<UUID, StockAvailabilityDtos.Colour> colours =
        includeColours ? loadColours(tenantId, batchIds) : Map.of();

    return batches.stream()
        .collect(
            Collectors.toMap(
                Batch::getId,
                batch ->
                    computeLot(
                        tenantId,
                        batch,
                        pieceBackedBatchIds.contains(batch.getId()),
                        filter,
                        vectorRows.getOrDefault(batch.getId(), List.of()),
                        pieceRows.getOrDefault(batch.getId(), List.of()),
                        qualityRows.getOrDefault(batch.getId(), List.of()),
                        qualityGrades,
                        commitments.get(batch.getId()),
                        colours.get(batch.getId())),
                (left, right) -> left,
                LinkedHashMap::new));
  }

  private LotComputation computeLot(
      UUID tenantId,
      Batch batch,
      boolean pieceBacked,
      Filter filter,
      List<StockUnitRepository.AvailabilityVectorRow> vectorRows,
      List<StockUnitRepository.AvailabilityPieceBreakdownRow> pieceRows,
      List<StockUnitRepository.AvailabilityQualityBreakdownRow> qualityRows,
      Map<UUID, QualityGradeReference> qualityGrades,
      BatchCommitmentQuantityService.Summary commitments,
      StockAvailabilityDtos.Colour colour) {
    var resolution = primaryMeasureService.resolve(batch);
    boolean hasPieces = pieceBacked || !vectorRows.isEmpty();
    List<StockUnitRepository.AvailabilityVectorRow> selectableRows =
        vectorRows.stream()
            .filter(row -> SELECTABLE_PIECE_STATUSES.contains(row.getStatus()))
            .toList();
    List<StockUnitRepository.AvailabilityVectorRow> responseRows =
        selectableRows.stream()
            .filter(row -> matchesQuality(row.getQualityGradeId(), filter))
            .toList();
    PhysicalAggregation responsePhysical = aggregatePhysical(responseRows);
    PhysicalAggregation allPhysical = aggregatePhysical(selectableRows);
    Fallback fallback = hasPieces ? null : fallback(batch, resolution.primaryMeasure());

    StockAvailabilityDtos.Physical physical =
        hasPieces
            ? responsePhysical.physical()
            : new StockAvailabilityDtos.Physical(fallback.kg(), fallback.metres(), 0);
    BigDecimal scalarPhysical =
        hasPieces
            ? primaryQuantity(allPhysical.physical(), resolution.primaryMeasure())
            : fallback.primaryQuantity();
    BigDecimal softIntent = commitments.softIntent();
    BigDecimal hardReserved = commitments.hardReserved();
    BigDecimal rawFree = scalarPhysical.subtract(softIntent).subtract(hardReserved);
    boolean overCommitted = rawFree.compareTo(BigDecimal.ZERO) < 0;
    BigDecimal free = overCommitted ? BigDecimal.ZERO : rawFree;
    if (overCommitted) {
      log.warn(
          "Over-committed stock lot: tenantId={}, batchId={}, deficit={}",
          tenantId,
          batch.getId(),
          rawFree.abs());
    }

    List<StockAvailabilityDtos.UnitMismatch> mismatches =
        mergeMismatches(
            commitments.unitMismatches(),
            allPhysical.mismatches(),
            fallback == null ? List.of() : fallback.mismatches());
    mismatches.stream()
        .filter(
            mismatch ->
                mismatch.source() == BatchUnitMismatchSource.PIECE_WEIGHT
                    || mismatch.source() == BatchUnitMismatchSource.PIECE_LENGTH
                    || mismatch.source() == BatchUnitMismatchSource.BATCH_QUANTITY)
        .forEach(
            mismatch ->
                log.warn(
                    "Excluded non-canonical physical quantity: tenantId={}, batchId={}, source={}, unit={}, quantity={}, rowCount={}",
                    tenantId,
                    batch.getId(),
                    mismatch.source(),
                    mismatch.unit(),
                    mismatch.quantity(),
                    mismatch.rowCount()));
    List<StockAvailabilityDtos.PieceBreakdown> pieceBreakdown =
        pieceBreakdown(pieceRows, resolution.primaryMeasure());
    List<StockAvailabilityDtos.QualityBreakdown> qualityBreakdown =
        qualityBreakdown(qualityRows, qualityGrades);
    StockAvailabilityDtos.Lot lot =
        new StockAvailabilityDtos.Lot(
            batch.getId(),
            batch.getBatchCode(),
            batch.getStatus().name(),
            new StockAvailabilityDtos.Product(batch.getProductId(), batch.getProductType()),
            colour,
            resolution.primaryMeasure(),
            resolution.primaryUnit(),
            physical,
            hasPieces ? PhysicalSource.PIECES : PhysicalSource.BATCH_FALLBACK,
            softIntent,
            hardReserved,
            free,
            overCommitted,
            mismatches,
            pieceBreakdown,
            qualityBreakdown);
    return new LotComputation(batch.getProductId(), lot);
  }

  private boolean matchesQuality(UUID gradeId, Filter filter) {
    if (filter.qualityGradeId() != null) {
      return filter.qualityGradeId().equals(gradeId);
    }
    if (filter.qualityUnassigned()) {
      return gradeId == null;
    }
    return true;
  }

  private PhysicalAggregation aggregatePhysical(
      List<StockUnitRepository.AvailabilityVectorRow> rows) {
    BigDecimal kg = BigDecimal.ZERO;
    BigDecimal metres = BigDecimal.ZERO;
    boolean hasKg = false;
    boolean hasMetres = false;
    long pieceCount = 0;
    List<UnitMismatch> mismatches = new ArrayList<>();
    for (var row : rows) {
      pieceCount += row.getPieceCount();
      var canonicalWeight =
          primaryMeasureService.toCanonical(
              row.getWeightQuantity(), row.getWeightUnit(), PrimaryMeasure.WEIGHT);
      if (canonicalWeight.isPresent()) {
        kg = kg.add(canonicalWeight.orElseThrow());
        hasKg = true;
      } else {
        mismatches.add(
            new UnitMismatch(
                BatchUnitMismatchSource.PIECE_WEIGHT,
                primaryMeasureService.normalizeUnit(row.getWeightUnit()),
                row.getWeightQuantity(),
                row.getPieceCount()));
      }
      if (row.getLengthQuantity() != null) {
        var canonicalLength =
            primaryMeasureService.toCanonical(
                row.getLengthQuantity(), row.getLengthUnit(), PrimaryMeasure.LENGTH);
        if (canonicalLength.isPresent()) {
          metres = metres.add(canonicalLength.orElseThrow());
          hasMetres = true;
        } else {
          mismatches.add(
              new UnitMismatch(
                  BatchUnitMismatchSource.PIECE_LENGTH,
                  primaryMeasureService.normalizeUnit(row.getLengthUnit()),
                  row.getLengthQuantity(),
                  row.getPieceCount()));
        }
      }
    }
    return new PhysicalAggregation(
        new StockAvailabilityDtos.Physical(
            hasKg ? kg : null, hasMetres ? metres : null, pieceCount),
        mismatches);
  }

  private Fallback fallback(Batch batch, PrimaryMeasure measure) {
    BigDecimal quantity = batch.getQuantity().subtract(batch.getConsumedQuantity());
    var canonical = primaryMeasureService.toCanonical(quantity, batch.getUnit(), measure);
    if (canonical.isEmpty()) {
      UnitMismatch mismatch =
          new UnitMismatch(
              BatchUnitMismatchSource.BATCH_QUANTITY,
              primaryMeasureService.normalizeUnit(batch.getUnit()),
              quantity,
              1);
      return new Fallback(null, null, BigDecimal.ZERO, List.of(mismatch));
    }
    BigDecimal value = canonical.orElseThrow();
    return measure == PrimaryMeasure.WEIGHT
        ? new Fallback(value, null, value, List.of())
        : new Fallback(null, value, value, List.of());
  }

  private BigDecimal primaryQuantity(
      StockAvailabilityDtos.Physical physical, PrimaryMeasure measure) {
    BigDecimal value = measure == PrimaryMeasure.WEIGHT ? physical.kg() : physical.metres();
    return value == null ? BigDecimal.ZERO : value;
  }

  private List<StockAvailabilityDtos.PieceBreakdown> pieceBreakdown(
      List<StockUnitRepository.AvailabilityPieceBreakdownRow> rows, PrimaryMeasure measure) {
    Map<PackageType, MutablePrimaryBreakdown> grouped = new HashMap<>();
    rows.forEach(
        row -> {
          MutablePrimaryBreakdown target =
              grouped.computeIfAbsent(
                  row.getPackageType(), ignored -> new MutablePrimaryBreakdown());
          target.count += row.getPieceCount();
          BigDecimal quantity =
              measure == PrimaryMeasure.WEIGHT ? row.getWeightQuantity() : row.getLengthQuantity();
          String unit =
              measure == PrimaryMeasure.WEIGHT ? row.getWeightUnit() : row.getLengthUnit();
          primaryMeasureService
              .toCanonical(quantity, unit, measure)
              .ifPresent(
                  canonical -> {
                    target.quantity = target.quantity.add(canonical);
                    target.hasQuantity = true;
                  });
        });
    return grouped.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(PackageType::name)))
        .map(
            entry ->
                new StockAvailabilityDtos.PieceBreakdown(
                    entry.getKey(),
                    entry.getValue().count,
                    entry.getValue().hasQuantity ? entry.getValue().quantity : null))
        .toList();
  }

  private List<StockAvailabilityDtos.QualityBreakdown> qualityBreakdown(
      List<StockUnitRepository.AvailabilityQualityBreakdownRow> rows,
      Map<UUID, QualityGradeReference> grades) {
    Map<UUID, MutableQualityBreakdown> grouped = new HashMap<>();
    MutableQualityBreakdown unassigned = new MutableQualityBreakdown(null);
    rows.forEach(
        row -> {
          MutableQualityBreakdown target;
          if (row.getQualityGradeId() == null) {
            target = unassigned;
          } else {
            QualityGradeReference grade = grades.get(row.getQualityGradeId());
            if (grade == null) {
              throw new IllegalStateException(
                  "Quality grade reference not found: " + row.getQualityGradeId());
            }
            target =
                grouped.computeIfAbsent(
                    grade.id(), ignored -> new MutableQualityBreakdown(toGrade(grade)));
          }
          target.add(row, primaryMeasureService);
        });
    List<StockAvailabilityDtos.QualityBreakdown> result =
        grouped.values().stream()
            .sorted(Comparator.comparingInt(value -> value.grade.rank()))
            .map(MutableQualityBreakdown::freeze)
            .collect(Collectors.toCollection(ArrayList::new));
    if (unassigned.pieceCount > 0) {
      result.add(unassigned.freeze());
    }
    return List.copyOf(result);
  }

  private StockAvailabilityDtos.QualityGrade toGrade(QualityGradeReference grade) {
    return new StockAvailabilityDtos.QualityGrade(
        grade.id(), grade.code(), grade.name(), grade.rank(), grade.saleable());
  }

  private Map<UUID, StockAvailabilityDtos.Colour> loadColours(UUID tenantId, List<UUID> batchIds) {
    return batchRepository.findColorReferencesByBatchIds(tenantId, batchIds).stream()
        .collect(
            Collectors.toMap(
                BatchRepository.BatchColorProjection::getBatchId,
                projection ->
                    new StockAvailabilityDtos.Colour(
                        projection.getColorId(),
                        projection.getColorCode(),
                        projection.getColorName(),
                        projection.getColorHex())));
  }

  private List<StockAvailabilityDtos.UnitMismatch> mergeMismatches(
      Collection<UnitMismatch> first,
      Collection<UnitMismatch> second,
      Collection<UnitMismatch> third) {
    Map<MismatchKey, MutableMismatch> grouped = new HashMap<>();
    List.of(first, second, third).stream()
        .flatMap(Collection::stream)
        .forEach(
            mismatch ->
                grouped
                    .computeIfAbsent(
                        new MismatchKey(mismatch.source(), mismatch.unit()),
                        ignored -> new MutableMismatch())
                    .add(mismatch.quantity(), mismatch.rowCount()));
    return grouped.entrySet().stream()
        .sorted(
            Map.Entry.comparingByKey(
                Comparator.comparing((MismatchKey key) -> key.source().name())
                    .thenComparing(MismatchKey::unit, Comparator.nullsFirst(String::compareTo))))
        .map(
            entry ->
                new StockAvailabilityDtos.UnitMismatch(
                    entry.getKey().source(),
                    entry.getKey().unit(),
                    entry.getValue().quantity,
                    entry.getValue().rowCount))
        .toList();
  }

  private StockAvailabilityDtos.Summary toSummary(ProductRow product, List<LotComputation> lots) {
    var resolution = primaryMeasureService.resolve(product.productType());
    BigDecimal kg = BigDecimal.ZERO;
    BigDecimal metres = BigDecimal.ZERO;
    boolean hasKg = false;
    boolean hasMetres = false;
    long pieceCount = 0;
    BigDecimal softIntent = BigDecimal.ZERO;
    BigDecimal hardReserved = BigDecimal.ZERO;
    BigDecimal free = BigDecimal.ZERO;
    boolean overCommitted = false;
    List<UnitMismatch> mismatches = new ArrayList<>();
    List<StockAvailabilityDtos.QualityBreakdown> quality = new ArrayList<>();
    for (LotComputation computation : lots) {
      var lot = computation.lot();
      if (lot.physical().kg() != null) {
        kg = kg.add(lot.physical().kg());
        hasKg = true;
      }
      if (lot.physical().metres() != null) {
        metres = metres.add(lot.physical().metres());
        hasMetres = true;
      }
      pieceCount += lot.physical().pieceCount();
      softIntent = softIntent.add(lot.softIntent());
      hardReserved = hardReserved.add(lot.hardReserved());
      free = free.add(lot.free());
      overCommitted |= lot.overCommitted();
      lot.unitMismatches()
          .forEach(
              mismatch ->
                  mismatches.add(
                      new UnitMismatch(
                          mismatch.source(),
                          mismatch.unit(),
                          mismatch.quantity(),
                          mismatch.rowCount())));
      quality.addAll(lot.qualityBreakdown());
    }
    return new StockAvailabilityDtos.Summary(
        new StockAvailabilityDtos.Product(product.productId(), product.productType()),
        resolution.primaryMeasure(),
        resolution.primaryUnit(),
        lots.size(),
        new StockAvailabilityDtos.Physical(
            hasKg ? kg : null, hasMetres ? metres : null, pieceCount),
        softIntent,
        hardReserved,
        free,
        overCommitted,
        mergeMismatches(mismatches, List.of(), List.of()),
        mergeQualityBreakdown(quality));
  }

  private List<StockAvailabilityDtos.QualityBreakdown> mergeQualityBreakdown(
      List<StockAvailabilityDtos.QualityBreakdown> rows) {
    Map<UUID, MutableQualityDto> grouped = new HashMap<>();
    MutableQualityDto unassigned = new MutableQualityDto(null);
    rows.forEach(
        row -> {
          MutableQualityDto target =
              row.grade() == null
                  ? unassigned
                  : grouped.computeIfAbsent(
                      row.grade().id(), ignored -> new MutableQualityDto(row.grade()));
          target.add(row);
        });
    List<StockAvailabilityDtos.QualityBreakdown> result =
        grouped.values().stream()
            .sorted(Comparator.comparingInt(value -> value.grade.rank()))
            .map(MutableQualityDto::freeze)
            .collect(Collectors.toCollection(ArrayList::new));
    if (unassigned.pieceCount > 0) {
      result.add(unassigned.freeze());
    }
    return List.copyOf(result);
  }

  private record LotComputation(UUID productId, StockAvailabilityDtos.Lot lot) {}

  private record PhysicalAggregation(
      StockAvailabilityDtos.Physical physical, List<UnitMismatch> mismatches) {}

  private record Fallback(
      BigDecimal kg,
      BigDecimal metres,
      BigDecimal primaryQuantity,
      List<UnitMismatch> mismatches) {}

  private record MismatchKey(BatchUnitMismatchSource source, String unit) {}

  private static final class MutableMismatch {
    private BigDecimal quantity = BigDecimal.ZERO;
    private long rowCount;

    private void add(BigDecimal value, long count) {
      quantity = quantity.add(value);
      rowCount += count;
    }
  }

  private static final class MutablePrimaryBreakdown {
    private BigDecimal quantity = BigDecimal.ZERO;
    private long count;
    private boolean hasQuantity;
  }

  private static final class MutableQualityBreakdown {
    private final StockAvailabilityDtos.QualityGrade grade;
    private long pieceCount;
    private BigDecimal kg = BigDecimal.ZERO;
    private BigDecimal metres = BigDecimal.ZERO;
    private boolean hasKg;
    private boolean hasMetres;

    private MutableQualityBreakdown(StockAvailabilityDtos.QualityGrade grade) {
      this.grade = grade;
    }

    private void add(
        StockUnitRepository.AvailabilityQualityBreakdownRow row,
        BatchPrimaryMeasureService measureService) {
      pieceCount += row.getPieceCount();
      measureService
          .toCanonical(row.getWeightQuantity(), row.getWeightUnit(), PrimaryMeasure.WEIGHT)
          .ifPresent(
              value -> {
                kg = kg.add(value);
                hasKg = true;
              });
      if (row.getLengthQuantity() != null) {
        measureService
            .toCanonical(row.getLengthQuantity(), row.getLengthUnit(), PrimaryMeasure.LENGTH)
            .ifPresent(
                value -> {
                  metres = metres.add(value);
                  hasMetres = true;
                });
      }
    }

    private StockAvailabilityDtos.QualityBreakdown freeze() {
      return new StockAvailabilityDtos.QualityBreakdown(
          grade, pieceCount, hasKg ? kg : null, hasMetres ? metres : null);
    }
  }

  private static final class MutableQualityDto {
    private final StockAvailabilityDtos.QualityGrade grade;
    private long pieceCount;
    private BigDecimal kg = BigDecimal.ZERO;
    private BigDecimal metres = BigDecimal.ZERO;
    private boolean hasKg;
    private boolean hasMetres;

    private MutableQualityDto(StockAvailabilityDtos.QualityGrade grade) {
      this.grade = grade;
    }

    private void add(StockAvailabilityDtos.QualityBreakdown row) {
      pieceCount += row.pieceCount();
      if (row.kg() != null) {
        kg = kg.add(row.kg());
        hasKg = true;
      }
      if (row.metres() != null) {
        metres = metres.add(row.metres());
        hasMetres = true;
      }
    }

    private StockAvailabilityDtos.QualityBreakdown freeze() {
      return new StockAvailabilityDtos.QualityBreakdown(
          grade, pieceCount, hasKg ? kg : null, hasMetres ? metres : null);
    }
  }
}
