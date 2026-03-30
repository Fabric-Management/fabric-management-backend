package com.fabricmanagement.production.execution.workorder.app.listener;

import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Listens to Approval system events and updates WorkOrder status accordingly. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkOrderApprovalEventListener {

  private final WorkOrderService workOrderService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onApprovalApproved(ApprovalApprovedEvent event) {
    if (!"WORK_ORDER".equals(event.getEntityType())) {
      return;
    }

    log.info(
        "WorkOrder {} approved by approval system, updating status to APPROVED",
        event.getEntityId());
    // Use the exposed changeStatus to transition it safely
    workOrderService.changeStatus(event.getEntityId(), WorkOrderStatus.APPROVED);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onApprovalRejected(ApprovalRejectedEvent event) {
    if (!"WORK_ORDER".equals(event.getEntityType())) {
      return;
    }

    log.info(
        "WorkOrder {} rejected by approval system, updating status to CANCELLED",
        event.getEntityId());
    // Move to cancelled if rejected (or maybe a specific REJECTED status if it exists)
    workOrderService.changeStatus(event.getEntityId(), WorkOrderStatus.CANCELLED);
  }
}
