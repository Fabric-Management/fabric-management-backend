package com.fabricmanagement.sales.salesorder.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderApprovalEventListener {

  private final SalesOrderService salesOrderService;
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
          try {
            log.info("Approval APPROVED for SALES_ORDER: {}", event.getEntityId());
            salesOrderService.confirmOrderAsSystem(event.getEntityId());
          } catch (com.fabricmanagement.sales.common.exception.OrderDomainException e) {
            if (e.getHttpStatus() == 409) {
              log.info(
                  "Sales order {} approval received but state transition failed (possibly already confirmed): {}",
                  event.getEntityId(),
                  e.getMessage());
            } else {
              log.error(
                  "Domain error processing SALES_ORDER approval for id: {}",
                  event.getEntityId(),
                  e);
              throw e; // Rethrow to mark as incomplete
            }
          } catch (Exception e) {
            log.error("Failed to process SALES_ORDER approval for id: {}", event.getEntityId(), e);
            throw e; // Rethrow to mark as incomplete
          }
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
          try {
            log.info("Approval REJECTED for SALES_ORDER: {}", event.getEntityId());
            salesOrderService.rejectOrder(event.getEntityId(), event.getRejectionReason());
          } catch (Exception e) {
            log.error("Failed to process SALES_ORDER rejection for id: {}", event.getEntityId(), e);
            throw e; // Rethrow to mark as incomplete
          }
        });
  }
}
