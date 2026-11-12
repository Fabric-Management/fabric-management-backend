package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import com.fabricmanagement.notification.hub.domain.port.PermissionRecipientPort;
import com.fabricmanagement.sales.ownership.domain.event.CustomerOwnershipTriageAgedEvent;
import com.fabricmanagement.sales.ownership.domain.event.CustomerOwnershipTriageOpenedEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesNotificationListener {

  private final NotificationHubService notificationHubService;
  private final DepartmentRecipientPort departmentRecipientPort;
  private final PermissionRecipientPort permissionRecipientPort;
  private final IdempotentEventHandler idempotentEventHandler;

  @ApplicationModuleListener
  public void onCustomerOwnershipTriageOpened(CustomerOwnershipTriageOpenedEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            idempotentEventHandler.executeOnce(
                event.getEventId(),
                this.getClass(),
                "onCustomerOwnershipTriageOpened",
                () -> notifySalesManagers(event)));
  }

  /**
   * Escalates an overdue case inside the tenant to everyone who can resolve it: holders of {@code
   * sales:assign-owner}. ADMIN always qualifies; the tenant widens the circle (CEO, general
   * manager) by granting that permission, so the recipients are tenant policy, not code.
   */
  @ApplicationModuleListener
  public void onCustomerOwnershipTriageAged(CustomerOwnershipTriageAgedEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            idempotentEventHandler.executeOnce(
                event.getEventId(),
                this.getClass(),
                "onCustomerOwnershipTriageAged",
                () -> notifyOwnershipAuthorities(event)));
  }

  private void notifySalesManagers(CustomerOwnershipTriageOpenedEvent event) {
    List<UUID> recipients =
        departmentRecipientPort.findManagersByDepartmentKeyword(event.getTenantId(), "SALES");
    if (recipients.isEmpty()) {
      return;
    }
    notificationHubService.notifyAll(
        recipients,
        event.getTenantId(),
        NotificationEventType.CUSTOMER_OWNERSHIP_TRIAGE_OPENED,
        Map.of(
            "customerName", event.getCustomerName(),
            "gapStartedAt", event.getGapStartedAt().toString(),
            "unassignedQuoteCount", String.valueOf(event.getUnassignedOpenQuoteCount())),
        event.getCustomerId(),
        "CUSTOMER");
  }

  private void notifyOwnershipAuthorities(CustomerOwnershipTriageAgedEvent event) {
    List<UUID> recipients =
        permissionRecipientPort
            .findUsersWithPermission(event.getTenantId(), PermissionKey.SALES_ASSIGN_OWNER)
            .stream()
            .sorted()
            .toList();
    if (recipients.isEmpty()) {
      return;
    }
    notificationHubService.notifyAll(
        recipients,
        event.getTenantId(),
        NotificationEventType.CUSTOMER_OWNERSHIP_TRIAGE_AGED,
        Map.of(
            "customerName", event.getCustomerName(),
            "gapStartedAt", event.getGapStartedAt().toString(),
            "ageHours", String.valueOf(event.getAgeHours()),
            "unassignedQuoteCount", String.valueOf(event.getUnassignedOpenQuoteCount())),
        event.getCustomerId(),
        "CUSTOMER");
  }
}
