package com.fabricmanagement.sales.salesorder.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import com.fabricmanagement.sales.salesorder.app.CustomerApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A manager's or finance decision on a sales order version that waits for the internal approval.
 * The decision applies to the exact approval request the version asked for; an approval of a
 * request that was cancelled because the order changed never reaches the customer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderApprovalEventListener {

  private final CustomerApprovalService customerApprovals;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void handleApprovalApproved(ApprovalApprovedEvent event) {
    if (!"SALES_ORDER".equals(event.getEntityType())) {
      return;
    }
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "handleApprovalApproved",
        () -> {
          log.info(
              "Internal approval {} given for sales order {}",
              event.getApprovalRequestId(),
              event.getEntityId());
          customerApprovals.onInternalApproval(event.getApprovalRequestId());
        });
  }

  @ApplicationModuleListener
  public void handleApprovalRejected(ApprovalRejectedEvent event) {
    if (!"SALES_ORDER".equals(event.getEntityType())) {
      return;
    }
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "handleApprovalRejected",
        () -> {
          log.info(
              "Internal approval {} declined for sales order {}",
              event.getApprovalRequestId(),
              event.getEntityId());
          customerApprovals.onInternalRejection(
              event.getApprovalRequestId(), event.getRejectionReason());
        });
  }
}
