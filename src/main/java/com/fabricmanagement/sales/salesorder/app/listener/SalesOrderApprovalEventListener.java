package com.fabricmanagement.sales.salesorder.app.listener;

import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderApprovalEventListener {

  private final SalesOrderService salesOrderService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleApprovalApproved(ApprovalApprovedEvent event) {
    if (!"SALES_ORDER".equals(event.getEntityType())) {
      return;
    }

    try {
      log.info("Approval APPROVED for SALES_ORDER: {}", event.getEntityId());
      salesOrderService.confirmOrderAsSystem(event.getEntityId());
    } catch (Exception e) {
      log.error("Failed to process SALES_ORDER approval for id: {}", event.getEntityId(), e);
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleApprovalRejected(ApprovalRejectedEvent event) {
    if (!"SALES_ORDER".equals(event.getEntityType())) {
      return;
    }

    try {
      log.info("Approval REJECTED for SALES_ORDER: {}", event.getEntityId());
      salesOrderService.rejectOrder(event.getEntityId(), event.getRejectionReason());
    } catch (Exception e) {
      log.error("Failed to process SALES_ORDER rejection for id: {}", event.getEntityId(), e);
    }
  }
}
