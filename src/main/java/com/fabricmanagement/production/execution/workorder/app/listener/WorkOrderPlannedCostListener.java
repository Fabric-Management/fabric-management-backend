package com.fabricmanagement.production.execution.workorder.app.listener;

import com.fabricmanagement.production.execution.workorder.app.WorkOrderPlannedCostTriggerService;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Triggers automatic planned cost calculation when a WorkOrder is approved.
 *
 * <p>Mirrors the pattern established by {@link WorkOrderCostBridgeListener} for actual cost on
 * completion:
 *
 * <ul>
 *   <li>{@code AFTER_COMMIT} — approval transaction commits first; planned cost runs in isolation
 *   <li>{@code REQUIRES_NEW} — separate transaction so cost failure cannot roll back approval
 *   <li>Exception swallowed — approval must never fail due to costing issues
 * </ul>
 *
 * <p>On failure, the error is logged with a manual retry path: {@code POST
 * /api/production/work-orders/{id}/recalculate-planned}.
 *
 * <p>Delegates all orchestration to {@link WorkOrderPlannedCostTriggerService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderPlannedCostListener {

  private final WorkOrderPlannedCostTriggerService plannedCostTriggerService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleWorkOrderApprovedEvent(WorkOrderApprovedEvent event) {
    log.info(
        "WorkOrderApprovedEvent: initiating planned cost calculation for WorkOrder {} ({})",
        event.getWorkOrderId(),
        event.getWorkOrderNumber());

    try {
      plannedCostTriggerService.triggerPlannedCost(event.getWorkOrderId());
    } catch (Exception e) {
      log.error(
          "Automatic planned cost calculation failed for WorkOrder {}. "
              + "Use POST /api/production/work-orders/{}/recalculate-planned after fixing configuration.",
          event.getWorkOrderId(),
          event.getWorkOrderId(),
          e);
      // Intentional: cost failure must not roll back the APPROVED status.
    }
  }
}
