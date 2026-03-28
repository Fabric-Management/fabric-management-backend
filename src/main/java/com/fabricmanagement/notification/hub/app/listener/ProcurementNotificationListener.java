package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoConfirmedEvent;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoDeliveryLateEvent;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoPartiallyReceivedEvent;
import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteReceivedEvent;
import com.fabricmanagement.procurement.rfq.domain.event.RfqDeadlineApproachingEvent;
import com.fabricmanagement.procurement.rfq.domain.event.RfqNoResponseEvent;
import com.fabricmanagement.procurement.rfq.domain.event.RfqSentEvent;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
  private final DepartmentRepository departmentRepository;
  private final UserQueryService userQueryService;
  private final SupplierRFQRepository rfqRepository;

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
          List<UUID> recipientIds = getDepartmentUsers(event.getTenantId(), "PROCUREMENT");
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
          rfqRepository
              .findByTenantIdAndIdAndIsActiveTrue(event.getTenantId(), event.getRfqId())
              .ifPresentOrElse(
                  rfq -> {
                    if (rfq.getCreatedBy() != null) {
                      notificationHubService.notifyAll(
                          List.of(rfq.getCreatedBy()),
                          event.getTenantId(),
                          NotificationEventType.SUPPLIER_QUOTE_RECEIVED,
                          Map.of(
                              "supplierName",
                              event.getSupplierName() != null ? event.getSupplierName() : ""),
                          event.getQuoteId(),
                          "QUOTE");
                    }
                  },
                  () ->
                      log.warn(
                          "RFQ not found for SupplierQuoteReceived (rfqId={})", event.getRfqId()));
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
          List<UUID> recipientIds = getDepartmentManagers(event.getTenantId(), "PROCUREMENT");
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
          List<UUID> recipientIds = getDepartmentManagers(event.getTenantId(), "PROCUREMENT");
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
              getDepartmentUsers(event.getTenantId(), "PROCUREMENT", "FINANCE");
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
          List<UUID> recipientIds = getDepartmentManagers(event.getTenantId(), "WAREHOUSE");
          if (!recipientIds.isEmpty()) {
            notificationHubService.notifyAll(
                recipientIds,
                event.getTenantId(),
                NotificationEventType.PO_PARTIALLY_RECEIVED,
                Map.of(
                    "poNumber", event.getPoNumber() != null ? event.getPoNumber() : "",
                    "received", String.valueOf(event.getReceivedItemCount()),
                    "total", String.valueOf(event.getTotalItemCount())),
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
          List<UUID> managerIds = getDepartmentManagers(event.getTenantId(), "PROCUREMENT");
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

  private List<UUID> getDepartmentUsers(UUID tenantId, String... deptCodes) {
    List<Department> deps =
        departmentRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
            .filter(
                d ->
                    matchesAny(d.getDepartmentCode(), deptCodes)
                        || matchesAny(d.getDepartmentName(), deptCodes))
            .toList();

    return deps.stream()
        .flatMap(d -> userQueryService.findByDepartments(tenantId, Set.of(d.getId())).stream())
        .map(UserDto::getId)
        .distinct()
        .toList();
  }

  private List<UUID> getDepartmentManagers(UUID tenantId, String... deptCodes) {
    List<Department> deps =
        departmentRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
            .filter(
                d ->
                    matchesAny(d.getDepartmentCode(), deptCodes)
                        || matchesAny(d.getDepartmentName(), deptCodes))
            .toList();

    List<UUID> managers =
        deps.stream().map(Department::getManagerId).filter(Objects::nonNull).distinct().toList();

    if (managers.isEmpty()) {
      return deps.stream()
          .flatMap(d -> userQueryService.findByDepartments(tenantId, Set.of(d.getId())).stream())
          .map(UserDto::getId)
          .distinct()
          .toList();
    }
    return managers;
  }

  private boolean matchesAny(String value, String[] targets) {
    if (value == null) return false;
    String upper = value.toUpperCase();
    for (String target : targets) {
      if (upper.contains(target.toUpperCase())) return true;
    }
    return false;
  }
}
