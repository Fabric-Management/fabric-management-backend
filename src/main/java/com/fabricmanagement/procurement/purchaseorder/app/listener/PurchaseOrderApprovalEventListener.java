package com.fabricmanagement.procurement.purchaseorder.app.listener;

import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderService;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Listens to Approval system events and updates PurchaseOrder status accordingly. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderApprovalEventListener {

  private static final String ENTITY_TYPE = "PURCHASE_ORDER";

  private final PurchaseOrderService purchaseOrderService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onApprovalApproved(ApprovalApprovedEvent event) {
    if (!ENTITY_TYPE.equals(event.getEntityType())) {
      return;
    }

    log.info(
        "PurchaseOrder {} approved by approval system, updating status to SENT",
        event.getEntityId());
    purchaseOrderService.changeStatusAsSystem(event.getEntityId(), PurchaseOrderStatus.SENT);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onApprovalRejected(ApprovalRejectedEvent event) {
    if (!ENTITY_TYPE.equals(event.getEntityType())) {
      return;
    }

    log.info(
        "PurchaseOrder {} rejected by approval system, updating status to REJECTED",
        event.getEntityId());
    purchaseOrderService.changeStatusAsSystem(event.getEntityId(), PurchaseOrderStatus.REJECTED);
  }
}
