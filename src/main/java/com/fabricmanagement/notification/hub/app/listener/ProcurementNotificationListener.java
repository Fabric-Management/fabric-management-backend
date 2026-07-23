package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoConfirmedEvent;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoDeliveryLateEvent;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoPartiallyReceivedEvent;
import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteReceivedEvent;
import com.fabricmanagement.procurement.rfq.domain.event.RfqDeadlineApproachingEvent;
import com.fabricmanagement.procurement.rfq.domain.event.RfqNoResponseEvent;
import com.fabricmanagement.procurement.rfq.domain.event.RfqSentEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Tedarik zinciri event listener'ları. */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProcurementNotificationListener {

  private final NotificationHubService notificationHubService;
  private final DepartmentRecipientPort departmentRecipientPort;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onRfqSent(RfqSentEvent event) {
    log.info(
        "NotificationHub ← RfqSent: rfq={} supplierCount={}",
        event.getRfqNumber(),
        event.getSupplierIds().size());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          List<UUID> recipientIds =
              departmentRecipientPort.findUsersByDepartmentKeyword(
                  event.getTenantId(), "PROCUREMENT");
          if (!recipientIds.isEmpty()) {
            notificationHubService.notifyAll(
                recipientIds,
                event.getTenantId(),
                NotificationEventType.RFQ_SENT,
                Map.of(
                    "rfqNumber",
                    event.getRfqNumber() != null ? event.getRfqNumber() : "",
                    "supplierCount",
                    String.valueOf(event.getSupplierIds().size())),
                event.getRfqId(),
                "RFQ");
          } else {
            log.warn("No recipients found for RfqSent (rfq={})", event.getRfqNumber());
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onSupplierQuoteReceived(SupplierQuoteReceivedEvent event) {
    log.info("NotificationHub ← SupplierQuoteReceived: supplier={}", event.getSupplierName());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          if (event.getRfqCreatedByUserId() != null) {
            notificationHubService.notifyAll(
                List.of(event.getRfqCreatedByUserId()),
                event.getTenantId(),
                NotificationEventType.SUPPLIER_QUOTE_RECEIVED,
                Map.of(
                    "supplierName", event.getSupplierName() != null ? event.getSupplierName() : ""),
                event.getQuoteId(),
                "QUOTE");
          } else {
            log.warn("No createdBy user for SupplierQuoteReceived (rfqId={})", event.getRfqId());
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onRfqDeadlineApproaching(RfqDeadlineApproachingEvent event) {
    log.warn(
        "NotificationHub ← RfqDeadlineApproaching: rfq={} hoursLeft={}",
        event.getRfqNumber(),
        event.getHoursRemaining());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          List<UUID> recipientIds =
              departmentRecipientPort.findManagersByDepartmentKeyword(
                  event.getTenantId(), "PROCUREMENT");
          if (!recipientIds.isEmpty()) {
            notificationHubService.notifyAll(
                recipientIds,
                event.getTenantId(),
                NotificationEventType.RFQ_DEADLINE_APPROACHING,
                Map.of(
                    "rfqNumber",
                    event.getRfqNumber() != null ? event.getRfqNumber() : "",
                    "hoursLeft",
                    String.valueOf(event.getHoursRemaining())),
                event.getRfqId(),
                "RFQ");
          } else {
            log.warn(
                "No recipients found for RfqDeadlineApproaching (rfq={})", event.getRfqNumber());
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onRfqNoResponse(RfqNoResponseEvent event) {
    log.warn(
        "NotificationHub ← RfqNoResponse: rfq={} supplier={}",
        event.getRfqNumber(),
        event.getSupplierName());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          List<UUID> recipientIds =
              departmentRecipientPort.findManagersByDepartmentKeyword(
                  event.getTenantId(), "PROCUREMENT");
          if (!recipientIds.isEmpty()) {
            notificationHubService.notifyAll(
                recipientIds,
                event.getTenantId(),
                NotificationEventType.RFQ_NO_RESPONSE,
                Map.of(
                    "rfqNumber", event.getRfqNumber() != null ? event.getRfqNumber() : "",
                    "supplierName", event.getSupplierName() != null ? event.getSupplierName() : ""),
                event.getRfqId(),
                "RFQ");
          } else {
            log.warn("No recipients found for RfqNoResponse (rfq={})", event.getRfqNumber());
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onPoConfirmed(PoConfirmedEvent event) {
    log.info("NotificationHub ← PoConfirmed: po={}", event.getPoNumber());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          List<UUID> recipientIds =
              departmentRecipientPort.findUsersByDepartmentKeyword(
                  event.getTenantId(), "PROCUREMENT", "FINANCE");
          if (!recipientIds.isEmpty()) {
            notificationHubService.notifyAll(
                recipientIds,
                event.getTenantId(),
                NotificationEventType.PO_CONFIRMED,
                Map.of("poNumber", event.getPoNumber() != null ? event.getPoNumber() : ""),
                event.getPurchaseOrderId(),
                "PO");
          } else {
            log.warn("No recipients found for PoConfirmed (po={})", event.getPoNumber());
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onPoPartiallyReceived(PoPartiallyReceivedEvent event) {
    log.info(
        "NotificationHub ← PoPartiallyReceived: po={} received={}/{}",
        event.getPoNumber(),
        event.getReceivedItemCount(),
        event.getTotalItemCount());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          List<UUID> recipientIds =
              departmentRecipientPort.findManagersByDepartmentKeyword(
                  event.getTenantId(), "WAREHOUSE");
          if (!recipientIds.isEmpty()) {
            notificationHubService.notifyAll(
                recipientIds,
                event.getTenantId(),
                NotificationEventType.PO_PARTIALLY_RECEIVED,
                Map.of(
                    "poNumber", event.getPoNumber() != null ? event.getPoNumber() : "",
                    "receivedItemCount", String.valueOf(event.getReceivedItemCount()),
                    "totalItemCount", String.valueOf(event.getTotalItemCount())),
                event.getPurchaseOrderId(),
                "PO");
          } else {
            log.warn("No recipients found for PoPartiallyReceived (po={})", event.getPoNumber());
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onPoDeliveryLate(PoDeliveryLateEvent event) {
    log.warn(
        "NotificationHub ← PoDeliveryLate [HIGH]: po={} supplier={} lateDays={}",
        event.getPoNumber(),
        event.getSupplierName(),
        event.getLateDays());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          List<UUID> managerIds =
              departmentRecipientPort.findManagersByDepartmentKeyword(
                  event.getTenantId(), "PROCUREMENT");
          if (!managerIds.isEmpty()) {
            notificationHubService.notifyAll(
                managerIds,
                event.getTenantId(),
                NotificationEventType.PO_DELIVERY_LATE,
                Map.of(
                    "poNumber", event.getPoNumber() != null ? event.getPoNumber() : "",
                    "supplierName", event.getSupplierName() != null ? event.getSupplierName() : "",
                    "lateDays", String.valueOf(event.getLateDays())),
                event.getPurchaseOrderId(),
                "PO");
          } else {
            log.warn("No recipients found for PoDeliveryLate (po={})", event.getPoNumber());
          }
        });
  }
}
