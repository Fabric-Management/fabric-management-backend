package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort.*;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlan;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlanState;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos.*;
import java.util.Arrays;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockUnitCutPlanViews {
  private final StockUnitCutPlanSources sources;
  private final StockUnitCutPlanLedger ledger;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort targets;

  public View view(StockUnitCutPlan plan, Context target) {
    return new View(
        plan.getId(),
        plan.getId() == null ? null : plan.getVersion(),
        TargetType.valueOf(plan.getTargetType()),
        plan.getTargetId(),
        plan.getTargetLineId(),
        plan.getState(),
        plan.sourceQuantity(),
        plan.getDeliveryQuantity(),
        plan.getCutDeliveryQuantity(),
        plan.remainderQuantity(),
        "M",
        plan.getSources().stream()
            .map(
                row ->
                    new Source(
                        row.stockUnitId(),
                        row.batchId(),
                        row.version(),
                        row.measurementRevision(),
                        row.length(),
                        row.weight(),
                        row.weightUnit(),
                        row.serialNumber(),
                        row.qualityGradeId(),
                        row.stockUnitId().equals(plan.getCutSourceId())))
            .toList(),
        plan.getActual() == null
            ? null
            : new Actual(
                plan.getActual().deliveryLength(),
                plan.getActual().remainderLength(),
                plan.getActual().lossLength(),
                plan.getActual().deliveryWeight(),
                plan.getActual().remainderWeight(),
                plan.getActual().lossWeight(),
                plan.getActual().reason()),
        plan.getDeliveryStockUnitId(),
        plan.getRemainderStockUnitId(),
        plan.getDeliveryStockUnitId() == null
            ? null
            : sources.unit(plan.getDeliveryStockUnitId()).getSerialNumber(),
        plan.getRemainderStockUnitId() == null
            ? null
            : sources.unit(plan.getRemainderStockUnitId()).getSerialNumber(),
        plan.getPreparedBy(),
        plan.getPreparedAt(),
        plan.getReviewedBy(),
        plan.getReviewedAt(),
        plan.getRecordedBy(),
        plan.getRecordedAt(),
        plan.getVerifiedBy(),
        plan.getVerifiedAt(),
        plan.getDispositionReason(),
        outputsHeld(plan),
        Arrays.stream(Action.values())
            .map(
                action -> {
                  var reason = reason(plan, target, action);
                  return new Capability(
                      action,
                      reason == null,
                      reason,
                      plan.getId() == null
                          ? target.route()
                          : com.fabricmanagement.common.infrastructure.web.AppRoutes.stockCutPlan(
                              plan.getId()),
                      action == Action.APPLY);
                })
            .toList());
  }

  public Reason reason(StockUnitCutPlan plan, Context target, Action action) {
    if (plan.getId() == null && action != Action.APPLY) return Reason.PLAN_ENDED;
    if (action == Action.CANCEL) {
      if (!target.canPrepare() && !target.canOperate()) return Reason.WRITE_REQUIRED;
      return plan.getState().beforeCut() ? null : Reason.PLAN_ENDED;
    }
    if (action == Action.VERIFY && plan.getState() == StockUnitCutPlanState.RECORDED) {
      if (!target.canOperate()) return Reason.PRODUCTION_WRITE_REQUIRED;
      // Cancelled targets still need physical verification; outputs remain held for disposition.
      return sources.cutMeasurementCurrent(plan) ? null : Reason.SOURCE_CHANGED;
    }
    if (action == Action.CORRECT
        && plan.getState() == StockUnitCutPlanState.DISPUTED
        && plan.getRecordedAt() != null)
      return target.canOperate() ? null : Reason.PRODUCTION_WRITE_REQUIRED;
    if (!target.active() || !Objects.equals(target.fingerprint(), plan.getTargetFingerprint()))
      return Reason.TARGET_CHANGED;
    if (plan.getState() == StockUnitCutPlanState.CANCELLED
        || plan.getState() == StockUnitCutPlanState.COMPLETED) return Reason.PLAN_ENDED;
    if (action == Action.APPLY) {
      if (!target.canPrepare()) return Reason.WRITE_REQUIRED;
      if (!target.editable()) return Reason.TARGET_NOT_EDITABLE;
      return plan.getId() == null ? null : Reason.PLAN_ENDED;
    }
    if (plan.getId() == null) return Reason.PLAN_ENDED;
    if (!target.canOperate()) return Reason.PRODUCTION_WRITE_REQUIRED;
    if (action == Action.CORRECT)
      return plan.getState() == StockUnitCutPlanState.DISPUTED && plan.getRecordedAt() != null
          ? null
          : Reason.ACTUAL_REQUIRED;
    if (!sources.current(plan)
        || !sources.ownsSources(plan)
        || !targets.sourcesCompatible(
            target.target(),
            plan.getSources().stream().map(StockUnitCutPlan.Source::stockUnitId).toList()))
      return Reason.SOURCE_CHANGED;
    if (action == Action.APPROVE)
      return plan.getState() == StockUnitCutPlanState.PREPARED ? null : Reason.REVIEW_REQUIRED;
    if (action == Action.RECORD) {
      if (plan.getState() != StockUnitCutPlanState.APPROVED) return Reason.REVIEW_REQUIRED;
      return target.executionReleased() && ledger.controlled(plan)
          ? null
          : Reason.EXECUTION_NOT_RELEASED;
    }
    return plan.getState() == StockUnitCutPlanState.DISPUTED
        ? Reason.MEASUREMENTS_DISPUTED
        : Reason.ACTUAL_REQUIRED;
  }

  public boolean outputsHeld(StockUnitCutPlan plan) {
    if (plan.getState() != StockUnitCutPlanState.COMPLETED) return false;
    return java.util.stream.Stream.of(plan.getDeliveryStockUnitId(), plan.getRemainderStockUnitId())
        .filter(Objects::nonNull)
        .map(sources::unit)
        .anyMatch(
            unit ->
                unit.getQualityDisposition()
                        != com.fabricmanagement.production.core.stockunit.domain.QualityDisposition
                            .RELEASED
                    || unit.getStatus()
                        == com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus
                            .ON_HOLD
                    || unit.getStatus()
                        == com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus
                            .QUARANTINE);
  }
}
