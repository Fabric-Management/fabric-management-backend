package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.core.inventory.api.facade.InventoryFacade;
import com.fabricmanagement.production.core.inventory.domain.enums.*;
import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.production.core.stockunit.domain.*;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitCutPlanException;
import com.fabricmanagement.production.core.stockunit.infra.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Physical replacement, allocations and the existing inventory ledger commit as one transaction.
 */
@Component
@RequiredArgsConstructor
public class StockUnitCutPlanLedger {
  private final StockUnitRepository pieces;
  private final StockUnitAllocationRepository allocations;
  private final BatchReservationRepository reservations;
  private final BatchRepository batches;
  private final BatchPrimaryMeasureService measures;
  private final StockUnitAuditLogRepository audit;
  private final InventoryFacade inventory;
  private final StockUnitSoftHoldPort holds;

  public boolean controlled(StockUnitCutPlan plan) {
    var active =
        allocations.findByTenantIdAndSalesOrderLineIdAndStatusOrderByAllocatedAtAscIdAsc(
            TenantContext.requireTenantId(),
            plan.getTargetLineId(),
            StockUnitAllocationStatus.ACTIVE);
    return plan.getSources().stream()
        .allMatch(
            source ->
                active.stream()
                    .anyMatch(
                        row ->
                            row.getStockUnitId().equals(source.stockUnitId())
                                && "M".equals(row.getUnit())
                                && row.getQuantity().compareTo(source.length()) == 0));
  }

  public void verify(
      StockUnitCutPlan plan, StockUnit source, boolean targetActive, UUID actor, Instant now) {
    var actual = plan.getActual();
    if (actual == null || !plan.conserved(actual))
      throw StockUnitCutPlanException.conflict(
          "CONSERVATION_MISMATCH",
          "Delivery, remainder and explicit loss must account for the complete source");
    var delivery =
        child(plan, source, actual.deliveryLength(), actual.deliveryWeight(), 1, targetActive);
    var remainder =
        actual.remainderLength().signum() == 0
            ? null
            : child(
                plan, source, actual.remainderLength(), actual.remainderWeight(), 2, targetActive);
    pieces.saveAndFlush(delivery);
    if (remainder != null) pieces.saveAndFlush(remainder);

    var sourceAllocation =
        allocations.findByTenantIdAndStockUnitIdInAndStatus(
            TenantContext.requireTenantId(),
            List.of(source.getId()),
            StockUnitAllocationStatus.ACTIVE);
    if (sourceAllocation.size() != 1
        || !sourceAllocation.getFirst().getSalesOrderLineId().equals(plan.getTargetLineId()))
      throw StockUnitCutPlanException.conflict(
          "OWNERSHIP_CHANGED", "The source is no longer allocated to this target");
    if (!sourceAllocation.isEmpty()) {
      var old = sourceAllocation.getFirst();
      var reservation =
          reservations
              .findByIdAndTenantId(old.getBatchReservationId(), TenantContext.requireTenantId())
              .orElseThrow(
                  () ->
                      StockUnitCutPlanException.conflict(
                          "OWNERSHIP_CHANGED", "The source reservation is missing"));
      BigDecimal released =
          old.getQuantity().subtract(targetActive ? actual.deliveryLength() : BigDecimal.ZERO);
      if (!targetActive && released.compareTo(reservation.getRemainingQuantity()) == 0)
        reservation.cancel();
      else reservation.releasePart(released);
      var batch =
          batches
              .findByIdAndTenantIdForUpdate(source.getBatchId(), TenantContext.requireTenantId())
              .orElseThrow();
      var releasedLot =
          measures
              .fromCanonical(released, batch.getUnit(), PrimaryMeasure.LENGTH)
              .orElseThrow(
                  () ->
                      StockUnitCutPlanException.conflict(
                          "UNIT_CHANGED", "The source lot unit changed"));
      if (releasedLot.signum() > 0) batch.release(releasedLot);
      old.release(actor, now, "VERIFIED_CUT");
      allocations.saveAndFlush(old);
      if (targetActive) {
        if (delivery.getQualityDisposition() == QualityDisposition.RELEASED) delivery.reserve();
        var allocation =
            StockUnitAllocation.allocate(
                delivery.getId(),
                source.getBatchId(),
                reservation.getId(),
                old.getSalesOrderId(),
                old.getSalesOrderLineId(),
                actual.deliveryLength(),
                "M",
                actor,
                now);
        allocation.setTenantId(TenantContext.requireTenantId());
        allocations.save(allocation);
      }
      reservations.save(reservation);
      if (actual.lossLength().signum() > 0) {
        var loss =
            measures
                .fromCanonical(actual.lossLength(), batch.getUnit(), PrimaryMeasure.LENGTH)
                .orElseThrow(
                    () ->
                        StockUnitCutPlanException.conflict(
                            "UNIT_CHANGED", "The source lot unit changed"));
        batch.adjustQuantity(loss.negate());
      }
      batches.save(batch);
    }

    source.completePlannedSplit();
    pieces.save(source);
    log(plan, source, plan.cutSource().length(), InventoryTransactionType.SPLIT_OUT, "SOURCE");
    log(plan, delivery, actual.deliveryLength(), InventoryTransactionType.SPLIT_IN, "DELIVERY");
    if (remainder != null)
      log(
          plan,
          remainder,
          actual.remainderLength(),
          InventoryTransactionType.SPLIT_IN,
          "REMAINDER");
    audit.save(
        StockUnitAuditLog.of(
            TenantContext.requireTenantId(),
            source.getId(),
            "VERIFIED_CUT",
            "children",
            null,
            delivery.getId() + ":" + (remainder == null ? "" : remainder.getId()),
            actor,
            0,
            actual.reason()));
    plan.complete(delivery.getId(), remainder == null ? null : remainder.getId(), actor, now);
    // Hard allocations now own delivery pieces; releasing old soft holds cannot release the
    // children.
    holds.releaseHolds(plan.getTargetLineId());
  }

  private StockUnit child(
      StockUnitCutPlan plan,
      StockUnit parent,
      BigDecimal length,
      BigDecimal weight,
      int ordinal,
      boolean activeTarget) {
    String identity = plan.getId().toString().replace("-", "");
    String prefix =
        parent.getSerialNumber() == null ? parent.getBarcode() : parent.getSerialNumber();
    prefix = prefix.substring(0, Math.min(prefix.length(), 63));
    QualityDisposition quality =
        activeTarget ? parent.getQualityDisposition() : QualityDisposition.PENDING_INSPECTION;
    var child =
        StockUnit.create(
            TenantContext.requireTenantId(),
            parent.getBatchId(),
            parent.getProductType(),
            "CUT-" + identity + "-" + ordinal,
            prefix + "-" + identity + "-" + ordinal,
            parent.getPackageType(),
            weight,
            null,
            parent.getUnit(),
            parent.getLocationId(),
            StockUnitSourceType.SPLIT,
            parent.getId(),
            quality);
    child.recordInitialLength(length, "M");
    child.inheritSplitGrade(parent.getQualityGradeId());
    if (quality != QualityDisposition.RELEASED) child.hold();
    return child;
  }

  private void log(
      StockUnitCutPlan plan,
      StockUnit piece,
      BigDecimal length,
      InventoryTransactionType type,
      String suffix) {
    var batch =
        batches
            .findByIdAndTenantIdForUpdate(piece.getBatchId(), TenantContext.requireTenantId())
            .orElseThrow();
    var quantity =
        measures
            .fromCanonical(length, batch.getUnit(), PrimaryMeasure.LENGTH)
            .orElseThrow(
                () ->
                    StockUnitCutPlanException.conflict(
                        "UNIT_CHANGED", "The inventory movement needs the lot's length unit"));
    inventory.logTransaction(
        TenantContext.requireTenantId(),
        piece.getBatchId(),
        type,
        quantity,
        batch.getUnit(),
        piece.getLocationId(),
        piece.getId(),
        "STOCK_UNIT",
        "Verified cut " + plan.getId(),
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        "CUT:" + plan.getId() + ":" + suffix);
  }
}
