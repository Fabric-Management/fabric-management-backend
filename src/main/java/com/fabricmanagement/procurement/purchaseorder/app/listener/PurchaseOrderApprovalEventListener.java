package com.fabricmanagement.procurement.purchaseorder.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderService;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Listens to Approval system events and updates PurchaseOrder status accordingly. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderApprovalEventListener {

  private static final String ENTITY_TYPE = "PURCHASE_ORDER";

  private final PurchaseOrderService purchaseOrderService;

  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void onApprovalApproved(ApprovalApprovedEvent event) {
    if (!ENTITY_TYPE.equals(event.getEntityType())) {
      return;
    }

    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onApprovalApproved",
        () -> {
          log.info(
              "PurchaseOrder {} approved by approval system, updating status to SENT",
              event.getEntityId());
          purchaseOrderService.changeStatusAsSystem(event.getEntityId(), PurchaseOrderStatus.SENT);
        });
  }

  @ApplicationModuleListener
  public void onApprovalRejected(ApprovalRejectedEvent event) {
    if (!ENTITY_TYPE.equals(event.getEntityType())) {
      return;
    }

    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onApprovalRejected",
        () -> {
          log.info(
              "PurchaseOrder {} rejected by approval system, updating status to REJECTED",
              event.getEntityId());
          purchaseOrderService.changeStatusAsSystem(
              event.getEntityId(), PurchaseOrderStatus.REJECTED);
        });
  }
}
