package com.fabricmanagement.production.execution.workorder.app.listener;

import com.fabricmanagement.production.execution.workorder.app.WorkOrderCostRecalculationService;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Triggers automatic cost calculation when a WorkOrder is completed.
 *
 * <p>Delegates all orchestration to {@link WorkOrderCostRecalculationService}. On failure, logs the
 * error but does NOT roll back the COMPLETED status — cost can be recalculated manually via the
 * {@code POST /api/production/work-orders/{id}/recalculate-cost} endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderCostBridgeListener {

  private final WorkOrderCostRecalculationService costRecalculationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleWorkOrderCompletedEvent(WorkOrderCompletedEvent event) {
    log.info(
        "WorkOrderCompletedEvent: initiating cost calculation for WorkOrder {}",
        event.getWorkOrderId());

    try {
      costRecalculationService.recalculateActualCost(event.getWorkOrderId());
    } catch (Exception e) {
      log.error(
          "Automatic cost calculation failed for WorkOrder {}. "
              + "Use POST /api/production/work-orders/{}/recalculate-cost after fixing configuration.",
          event.getWorkOrderId(),
          event.getWorkOrderId(),
          e);
      // Intentional: cost failure must not roll back the COMPLETED status.
    }
  }
}
