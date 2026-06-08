package com.fabricmanagement.production.execution.workorder.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderPlannedCostTriggerService;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Triggers automatic planned cost calculation when a WorkOrder is approved.
 *
 * <p>Mirrors the pattern established by {@link WorkOrderCostBridgeListener} for actual cost on
 * completion:
 *
 * <ul>
 *   <li>{@code @ApplicationModuleListener} — event runs asynchronously after the approval
 *       transaction commits
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
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void handleWorkOrderApprovedEvent(WorkOrderApprovedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "handleWorkOrderApprovedEvent",
        () -> {
          log.info(
              "WorkOrderApprovedEvent: initiating planned cost calculation for WorkOrder {} ({})",
              event.getWorkOrderId(),
              event.getWorkOrderNumber());

          plannedCostTriggerService.triggerPlannedCost(event.getWorkOrderId());
        });
  }
}
