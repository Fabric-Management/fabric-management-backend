package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort;
import com.fabricmanagement.production.core.stockunit.domain.*;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitCutPlanException;
import com.fabricmanagement.production.core.stockunit.infra.repository.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Called only behind the commercial-root, inventory and lot writer locks. */
@Component
@RequiredArgsConstructor
public class StockUnitCutPlanSources {
  private final StockUnitRepository pieces;
  private final StockUnitCutRepository cuts;
  private final StockUnitOwnershipPort ownership;
  private final BatchPrimaryMeasureService measures;
  private final StockUnitSoftHoldRepository holds;
  private final StockUnitAllocationRepository allocations;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort targets;
  private final com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService grades;

  public List<StockUnitCutPlan.Source> capture(List<UUID> ids, UUID ownLine, UUID session) {
    if (ids == null || ids.isEmpty() || ids.size() != new HashSet<>(ids).size())
      throw StockUnitCutPlanException.invalid(
          "SOURCES_INVALID", "Distinct source rolls are required");
    ownership.assertAvailable(ids, ownLine, session);
    return ids.stream()
        .map(this::unit)
        .map(
            unit -> {
              eligible(unit);
              return new StockUnitCutPlan.Source(
                  unit.getId(),
                  unit.getBatchId(),
                  unit.getVersion(),
                  length(unit),
                  unit.getCurrentWeight(),
                  unit.getUnit(),
                  unit.getSerialNumber(),
                  unit.getQualityGradeId(),
                  unit.getMeasurementRevision());
            })
        .toList();
  }

  public boolean current(StockUnitCutPlan plan) {
    try {
      for (var evidence : plan.getSources()) {
        var unit = unit(evidence.stockUnitId());
        eligible(unit);
        if (!unit.getBatchId().equals(evidence.batchId())
            || length(unit).compareTo(evidence.length()) != 0
            || unit.getCurrentWeight().compareTo(evidence.weight()) != 0
            || !Objects.equals(unit.getQualityGradeId(), evidence.qualityGradeId())
            || !Objects.equals(unit.getMeasurementRevision(), evidence.measurementRevision())
            || !unit.getUnit().equals(evidence.weightUnit())) return false;
      }
      return true;
    } catch (StockUnitCutPlanException failure) {
      return false;
    }
  }

  public boolean ownsSources(StockUnitCutPlan plan) {
    var ids = plan.getSources().stream().map(StockUnitCutPlan.Source::stockUnitId).toList();
    var owned =
        holds.findActiveByStockUnitIds(TenantContext.requireTenantId(), ids).stream()
            .filter(row -> row.getOwnerLineId().equals(plan.getTargetLineId()))
            .map(StockUnitSoftHold::getStockUnitId)
            .collect(java.util.stream.Collectors.toSet());
    allocations
        .findByTenantIdAndStockUnitIdInAndStatus(
            TenantContext.requireTenantId(), ids, StockUnitAllocationStatus.ACTIVE)
        .stream()
        .filter(row -> row.getSalesOrderLineId().equals(plan.getTargetLineId()))
        .map(StockUnitAllocation::getStockUnitId)
        .forEach(owned::add);
    return owned.containsAll(ids);
  }

  public void lock(StockUnitCutPlan plan) {
    ownership.lockLots(plan.getSources().stream().map(StockUnitCutPlan.Source::batchId).toList());
  }

  public boolean cutMeasurementCurrent(StockUnitCutPlan plan) {
    try {
      var unit = unit(plan.getCutSourceId());
      return length(unit).compareTo(plan.cutSource().length()) == 0
          && unit.getCurrentWeight().compareTo(plan.cutSource().weight()) == 0
          && unit.getUnit().equals(plan.cutSource().weightUnit())
          && Set.of(
                  StockUnitStatus.RESERVED,
                  StockUnitStatus.AVAILABLE,
                  StockUnitStatus.ON_HOLD,
                  StockUnitStatus.QUARANTINE)
              .contains(unit.getStatus());
    } catch (StockUnitCutPlanException failure) {
      return false;
    }
  }

  public StockUnit unit(UUID id) {
    return pieces
        .findByIdAndTenantIdAndIsActiveTrue(id, TenantContext.requireTenantId())
        .orElseThrow(
            () ->
                StockUnitCutPlanException.conflict(
                    "SOURCE_CHANGED", "A source roll is no longer present"));
  }

  public List<com.fabricmanagement.production.core.stockunit.dto.StockUnitCutSessionDtos.Candidate>
      candidates(
          com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort.Context context,
          UUID session) {
    var ids = targets.candidateIds(context.target());
    if (ids.isEmpty()) return List.of();
    var leased = ownership.provisionallyHeld(context.productId(), session);
    var budgets = targets.sourceBudgets(context.target(), session);
    var held =
        holds.findActiveByStockUnitIds(TenantContext.requireTenantId(), ids).stream()
            .filter(row -> !row.getOwnerLineId().equals(context.target().lineId()))
            .map(StockUnitSoftHold::getStockUnitId)
            .collect(java.util.stream.Collectors.toSet());
    var hard =
        allocations
            .findByTenantIdAndStockUnitIdInAndStatus(
                TenantContext.requireTenantId(), ids, StockUnitAllocationStatus.ACTIVE)
            .stream()
            .filter(row -> !row.getSalesOrderLineId().equals(context.target().lineId()))
            .map(StockUnitAllocation::getStockUnitId)
            .collect(java.util.stream.Collectors.toSet());
    return pieces
        .findByTenantIdAndIdInAndIsActiveTrue(TenantContext.requireTenantId(), ids)
        .stream()
        .map(
            unit -> {
              var availability =
                  com.fabricmanagement.production.core.stockunit.dto.StockUnitCutSessionDtos
                      .Availability.AVAILABLE;
              if (leased.contains(unit.getId())
                  || held.contains(unit.getId())
                  || hard.contains(unit.getId()))
                availability =
                    com.fabricmanagement.production.core.stockunit.dto.StockUnitCutSessionDtos
                        .Availability.HELD;
              else if (unit.getQualityDisposition() != QualityDisposition.RELEASED
                  || unit.isFlagged())
                availability =
                    com.fabricmanagement.production.core.stockunit.dto.StockUnitCutSessionDtos
                        .Availability.UNAVAILABLE;
              return new com.fabricmanagement.production.core.stockunit.dto.StockUnitCutSessionDtos
                  .Candidate(
                  unit.getId(),
                  unit.getBatchId(),
                  unit.getSerialNumber(),
                  length(unit),
                  "M",
                  availability,
                  budgets.get(unit.getBatchId()));
            })
        .toList();
  }

  private BigDecimal length(StockUnit unit) {
    return measures
        .toCanonical(unit.getLength(), unit.getLengthUnit(), PrimaryMeasure.LENGTH)
        .filter(value -> value.signum() > 0)
        .orElseThrow(
            () ->
                StockUnitCutPlanException.conflict(
                    "MEASUREMENT_REQUIRED", "A source needs a verified length"));
  }

  public BigDecimal metres(BigDecimal value, String unit) {
    return measures
        .toCanonical(value, unit, PrimaryMeasure.LENGTH)
        .orElseThrow(
            () ->
                StockUnitCutPlanException.invalid(
                    "LENGTH_REQUIRED", "Cut plans require an exact length unit"));
  }

  public boolean deliveryReady(StockUnit unit, BigDecimal quantity) {
    try {
      eligible(unit);
      return unit.getStatus() == StockUnitStatus.RESERVED && length(unit).compareTo(quantity) == 0;
    } catch (StockUnitCutPlanException invalid) {
      return false;
    }
  }

  private void eligible(StockUnit unit) {
    if (unit.getProductType() != com.fabricmanagement.product.core.domain.ProductType.FABRIC
        || unit.getPackageType() != PackageType.ROLL
        || unit.getQualityDisposition() != QualityDisposition.RELEASED
        || !Set.of(StockUnitStatus.AVAILABLE, StockUnitStatus.RESERVED).contains(unit.getStatus())
        || unit.isFlagged()
        || unit.getCurrentWeight() == null
        || unit.getCurrentWeight().signum() <= 0)
      throw StockUnitCutPlanException.conflict(
          "SOURCE_CHANGED", "Use a released, measured fabric roll");
    if (unit.getQualityGradeId() != null
        && grades
            .findActiveReferenceById(unit.getQualityGradeId())
            .filter(
                com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService
                        .QualityGradeReference
                    ::saleable)
            .isEmpty())
      throw StockUnitCutPlanException.conflict(
          "SOURCE_CHANGED", "The source grade is not saleable");
    if (cuts
        .findByTenantIdAndStockUnitIdInAndIsActiveTrue(
            TenantContext.requireTenantId(), List.of(unit.getId()))
        .stream()
        .anyMatch(cut -> !cut.isRemainingVerified() || cut.getRemainingLength().signum() == 0))
      throw StockUnitCutPlanException.conflict(
          "SOURCE_CHANGED", "An unverified cut cannot be planned again");
  }
}
