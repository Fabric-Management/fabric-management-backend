package com.fabricmanagement.production.execution.workorder.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Listens to Approval system events and updates WorkOrder status accordingly. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkOrderApprovalEventListener {

  private final WorkOrderService workOrderService;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void onApprovalApproved(ApprovalApprovedEvent event) {
    if (!"WORK_ORDER".equals(event.getEntityType())) {
      return;
    }

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            idempotentHandler.executeOnce(
                event.getEventId(),
                this.getClass(),
                "onApprovalApproved",
                () -> {
                  log.info(
                      "WorkOrder {} approved by approval system, updating status to APPROVED",
                      event.getEntityId());
                  // Use the exposed changeStatus to transition it safely
                  workOrderService.changeStatus(event.getEntityId(), WorkOrderStatus.APPROVED);
                }));
  }

  @ApplicationModuleListener
  public void onApprovalRejected(ApprovalRejectedEvent event) {
    if (!"WORK_ORDER".equals(event.getEntityType())) {
      return;
    }

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            idempotentHandler.executeOnce(
                event.getEventId(),
                this.getClass(),
                "onApprovalRejected",
                () -> {
                  log.info(
                      "WorkOrder {} rejected by approval system, updating status to CANCELLED",
                      event.getEntityId());
                  // Move to cancelled if rejected (or maybe a specific REJECTED status if it
                  // exists)
                  workOrderService.changeStatus(event.getEntityId(), WorkOrderStatus.CANCELLED);
                }));
  }
}
