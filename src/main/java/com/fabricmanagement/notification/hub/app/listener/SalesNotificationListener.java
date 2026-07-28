package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
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
}
