package com.fabricmanagement.production.core.batch.api.query;

import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchFinishedWidthMeasurement;
import com.fabricmanagement.production.core.batch.domain.BatchReservation;
import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import com.fabricmanagement.production.core.batch.domain.LotCompatibilityConfirmation;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.core.batch.domain.ReservationStatus;
import com.fabricmanagement.production.core.batch.infra.repository.BatchFinishedWidthMeasurementRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.core.batch.infra.repository.LotCompatibilityConfirmationRepository;
import com.fabricmanagement.production.core.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitAllocation;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitAllocationStatus;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCut;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitAllocationRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public production read contract for whole-piece quantity proposals (SOI D2). It reports, piece by
 * piece, what may be offered and why the rest may not: evidence that is missing stays UNKNOWN and
 * never becomes "none" or "suitable". Nothing is reserved here.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProposalStockQueryService {

  private static final Set<BatchStatus> SALEABLE_LOT_STATUSES =
      Set.of(BatchStatus.AVAILABLE, BatchStatus.RESERVED);

  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;
  private final StockUnitCutRepository cutRepository;
  private final StockUnitAllocationRepository allocationRepository;
  private final BatchReservationRepository reservationRepository;
  private final BatchFinishedWidthMeasurementRepository widthRepository;
  private final LotCompatibilityConfirmationRepository compatibilityRepository;
  private final QualityGradeQueryService gradeQueryService;
  private final BatchPrimaryMeasureService measureService;

  /**
   * Lots of the product whose colour matches the requirement. A line without colour matches lots
   * without colour only. A width requirement drops lots measured at another width and marks lots
   * without a measurement as unknown.
   */
  public ProposalStock find(ProposalStockQuery query) {
    List<Batch> batches =
        batchRepository
            .findByTenantIdAndProductIdAndIsActiveTrue(query.tenantId(), query.productId())
            .stream()
            .filter(batch -> SALEABLE_LOT_STATUSES.contains(batch.getStatus()))
            .filter(batch -> Objects.equals(batch.getColorId(), query.colorId()))
            .sorted(Comparator.comparing(Batch::getId))
            .toList();
    if (batches.isEmpty()) {
      return new ProposalStock(List.of(), List.of());
    }
    List<UUID> batchIds = batches.stream().map(Batch::getId).toList();
    Map<UUID, BatchFinishedWidthMeasurement> latestWidth = latestWidths(query.tenantId(), batchIds);
    Map<UUID, List<StockUnit>> unitsByBatch =
        stockUnitRepository
            .findByTenantIdAndBatchIdInAndIsActiveTrue(query.tenantId(), batchIds)
            .stream()
            .collect(Collectors.groupingBy(StockUnit::getBatchId));
    List<UUID> unitIds =
        unitsByBatch.values().stream().flatMap(Collection::stream).map(StockUnit::getId).toList();
    Map<UUID, StockUnitCut> latestCut = latestCuts(query.tenantId(), unitIds);
    Set<UUID> lotsWithAnonymousReservation =
        lotsWithAnonymousReservation(query.tenantId(), batchIds);
    Map<UUID, QualityGradeQueryService.QualityGradeReference> grades =
        gradeQueryService
            .findReferencesByIds(
                unitsByBatch.values().stream()
                    .flatMap(Collection::stream)
                    .map(StockUnit::getQualityGradeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(
                Collectors.toMap(
                    QualityGradeQueryService.QualityGradeReference::id, Function.identity()));

    List<ProposalLot> lots =
        batches.stream()
            .map(
                batch ->
                    toLot(
                        query,
                        batch,
                        latestWidth.get(batch.getId()),
                        unitsByBatch.getOrDefault(batch.getId(), List.of()),
                        latestCut,
                        grades,
                        lotsWithAnonymousReservation.contains(batch.getId())))
            .flatMap(Optional::stream)
            .toList();
    return new ProposalStock(lots, compatibleGroups(query, lots));
  }

  /** The canonical dimension a product type is measured in (fabric: length, fibre/yarn: weight). */
  public Optional<PrimaryMeasure> measureFor(
      com.fabricmanagement.product.core.domain.ProductType productType) {
    return measureService
        .findResolution(productType)
        .map(BatchPrimaryMeasureService.Resolution::primaryMeasure);
  }

  public String canonicalUnit(PrimaryMeasure measure) {
    return measureService.canonicalUnit(measure);
  }

  /** Exact metric conversion into the canonical unit; empty when no exact conversion exists. */
  public Optional<BigDecimal> toCanonical(
      BigDecimal quantity, String unit, PrimaryMeasure measure) {
    return measureService.toCanonical(quantity, unit, measure);
  }

  public Optional<BigDecimal> fromCanonical(
      BigDecimal quantity, String unit, PrimaryMeasure measure) {
    return measureService.fromCanonical(quantity, unit, measure);
  }

  private Optional<ProposalLot> toLot(
      ProposalStockQuery query,
      Batch batch,
      BatchFinishedWidthMeasurement width,
      List<StockUnit> units,
      Map<UUID, StockUnitCut> latestCut,
      Map<UUID, QualityGradeQueryService.QualityGradeReference> grades,
      boolean anonymousReservation) {
    WidthEvidence widthEvidence = WidthEvidence.NOT_REQUIRED;
    if (query.finishedWidth() != null) {
      if (width == null) {
        widthEvidence = WidthEvidence.UNKNOWN;
      } else if (width.getWidthValue().compareTo(query.finishedWidth()) == 0
          && width.getWidthUnit().equals(normalise(query.finishedWidthUnit()))) {
        widthEvidence = WidthEvidence.MATCH;
      } else {
        return Optional.empty();
      }
    }
    var resolution = measureService.findResolution(batch.getProductType());
    if (resolution.isEmpty()) {
      return Optional.empty();
    }
    PrimaryMeasure measure = resolution.get().primaryMeasure();
    WidthEvidence lotWidth = widthEvidence;
    List<ProposalPiece> pieces =
        units.stream()
            .filter(unit -> unit.getStatus() != StockUnitStatus.DEPLETED)
            .filter(unit -> unit.getStatus() != StockUnitStatus.DISPOSED)
            .sorted(Comparator.comparing(StockUnit::getId))
            .map(
                unit ->
                    toPiece(
                        unit,
                        measure,
                        lotWidth,
                        latestCut.get(unit.getId()),
                        grades,
                        anonymousReservation))
            .toList();
    return Optional.of(
        new ProposalLot(
            batch.getId(),
            batch.getBatchCode(),
            batch.getProductionDate(),
            measure,
            resolution.get().primaryUnit(),
            lotWidth,
            pieces));
  }

  private ProposalPiece toPiece(
      StockUnit unit,
      PrimaryMeasure measure,
      WidthEvidence width,
      StockUnitCut cut,
      Map<UUID, QualityGradeQueryService.QualityGradeReference> grades,
      boolean anonymousReservation) {
    BigDecimal value =
        measure == PrimaryMeasure.LENGTH
            ? measureService
                .toCanonical(unit.getLength(), unit.getLengthUnit(), measure)
                .orElse(null)
            : measureService
                .toCanonical(unit.getCurrentWeight(), unit.getUnit(), measure)
                .orElse(null);
    PieceState state = PieceState.ELIGIBLE;
    String reason = null;
    if (unit.getStatus() == StockUnitStatus.RESERVED) {
      state = PieceState.EXCLUDED;
      reason = "ALLOCATED";
    } else if (unit.getStatus() == StockUnitStatus.PARTIAL) {
      state = PieceState.UNKNOWN;
      reason = "REMAINING_LENGTH_UNVERIFIED";
    } else if (unit.getStatus() != StockUnitStatus.AVAILABLE) {
      state = PieceState.EXCLUDED;
      reason = "STATUS_" + unit.getStatus().name();
    } else if (unit.getQualityDisposition() == QualityDisposition.PENDING_INSPECTION) {
      state = PieceState.UNKNOWN;
      reason = "QUALITY_PENDING";
    } else if (unit.getQualityDisposition() != QualityDisposition.RELEASED) {
      state = PieceState.EXCLUDED;
      reason = "QUALITY_" + unit.getQualityDisposition().name();
    } else if (unit.getQualityGradeId() != null
        && grades.containsKey(unit.getQualityGradeId())
        && !grades.get(unit.getQualityGradeId()).saleable()) {
      state = PieceState.EXCLUDED;
      reason = "GRADE_NOT_SALEABLE";
    } else if (unit.isFlagged()) {
      state = PieceState.UNKNOWN;
      reason = "FLAGGED";
    } else if (measure == PrimaryMeasure.LENGTH
        && cut != null
        && cut.isRemainingVerified()
        && cut.getRemainingLength().signum() == 0) {
      state = PieceState.EXCLUDED;
      reason = "CUT_CONSUMED";
    } else if (measure == PrimaryMeasure.LENGTH && cut != null && !cut.isRemainingVerified()) {
      state = PieceState.UNKNOWN;
      reason = "CUT_REMAINING_UNVERIFIED";
    } else if (value == null || value.signum() <= 0) {
      state = PieceState.UNKNOWN;
      reason = "MEASURE_MISSING";
    } else if (width == WidthEvidence.UNKNOWN) {
      state = PieceState.UNKNOWN;
      reason = "WIDTH_NOT_MEASURED";
    } else if (anonymousReservation) {
      state = PieceState.UNKNOWN;
      reason = "LOT_RESERVED_WITHOUT_PIECES";
    }
    return new ProposalPiece(
        unit.getId(), unit.getBarcode(), value, state, reason, unit.getVersion());
  }

  private List<Set<UUID>> compatibleGroups(ProposalStockQuery query, List<ProposalLot> lots) {
    Set<UUID> candidateIds = lots.stream().map(ProposalLot::batchId).collect(Collectors.toSet());
    return compatibilityRepository
        .findByTenantIdAndRevokedAtIsNullAndIsActiveTrue(query.tenantId())
        .stream()
        .filter(LotCompatibilityConfirmation::isEffective)
        .filter(
            confirmation ->
                confirmation.getCustomerId() == null
                    || confirmation.getCustomerId().equals(query.customerId()))
        .map(
            confirmation ->
                confirmation.getBatchIds().stream()
                    .filter(candidateIds::contains)
                    .collect(Collectors.toUnmodifiableSet()))
        .filter(group -> group.size() >= 2)
        .toList();
  }

  /**
   * Lots holding an open quantity reservation that is not tied to named pieces (work orders,
   * samples, legacy flows). Which pieces that quantity will take is unknown, so none of the lot's
   * free pieces may be offered as certain (SOI IK-15).
   */
  private Set<UUID> lotsWithAnonymousReservation(UUID tenantId, List<UUID> batchIds) {
    Set<UUID> pieceBacked =
        allocationRepository
            .findByTenantIdAndBatchIdInAndStatus(
                tenantId, batchIds, StockUnitAllocationStatus.ACTIVE)
            .stream()
            .map(StockUnitAllocation::getBatchReservationId)
            .collect(Collectors.toSet());
    return reservationRepository
        .findByTenantIdAndBatchIdInAndIsActiveTrueOrderById(tenantId, batchIds)
        .stream()
        .filter(
            reservation ->
                reservation.getStatus() == ReservationStatus.ACTIVE
                    || reservation.getStatus() == ReservationStatus.PARTIALLY_CONSUMED)
        .filter(reservation -> reservation.getRemainingQuantity().signum() > 0)
        .filter(reservation -> !pieceBacked.contains(reservation.getId()))
        .map(BatchReservation::getBatchId)
        .collect(Collectors.toSet());
  }

  private Map<UUID, BatchFinishedWidthMeasurement> latestWidths(
      UUID tenantId, List<UUID> batchIds) {
    return widthRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(tenantId, batchIds).stream()
        .collect(
            Collectors.toMap(
                BatchFinishedWidthMeasurement::getBatchId,
                Function.identity(),
                (left, right) ->
                    left.getMeasuredAt().isAfter(right.getMeasuredAt()) ? left : right));
  }

  private Map<UUID, StockUnitCut> latestCuts(UUID tenantId, List<UUID> unitIds) {
    if (unitIds.isEmpty()) {
      return Map.of();
    }
    return cutRepository.findByTenantIdAndStockUnitIdInAndIsActiveTrue(tenantId, unitIds).stream()
        .collect(
            Collectors.toMap(
                StockUnitCut::getStockUnitId,
                Function.identity(),
                (left, right) ->
                    left.getRecordedAt().isAfter(right.getRecordedAt()) ? left : right));
  }

  private static String normalise(String unit) {
    return unit == null ? null : unit.trim().toUpperCase(Locale.ROOT);
  }

  public record ProposalStockQuery(
      UUID tenantId,
      UUID productId,
      UUID colorId,
      BigDecimal finishedWidth,
      String finishedWidthUnit,
      UUID customerId) {}

  public record ProposalStock(List<ProposalLot> lots, List<Set<UUID>> confirmedCompatibleGroups) {}

  public record ProposalLot(
      UUID batchId,
      String lotNo,
      Instant productionDate,
      PrimaryMeasure measure,
      String canonicalUnit,
      WidthEvidence widthEvidence,
      List<ProposalPiece> pieces) {}

  public record ProposalPiece(
      UUID stockUnitId,
      String pieceNo,
      BigDecimal canonicalMeasure,
      PieceState state,
      String reason,
      Long version) {}

  public enum PieceState {
    ELIGIBLE,
    UNKNOWN,
    EXCLUDED
  }

  public enum WidthEvidence {
    NOT_REQUIRED,
    MATCH,
    UNKNOWN
  }
}
