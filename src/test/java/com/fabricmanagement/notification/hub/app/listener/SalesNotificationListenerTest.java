package com.fabricmanagement.notification.hub.app.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import com.fabricmanagement.notification.hub.domain.port.PermissionRecipientPort;
import com.fabricmanagement.sales.ownership.domain.event.CustomerOwnershipTriageAgedEvent;
import com.fabricmanagement.sales.ownership.domain.event.CustomerOwnershipTriageOpenedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesNotificationListenerTest {

  @Mock private NotificationHubService notificationHubService;
  @Mock private DepartmentRecipientPort departmentRecipientPort;
  @Mock private PermissionRecipientPort permissionRecipientPort;
  @Mock private IdempotentEventHandler idempotentEventHandler;

  @Test
  void routesTheSalesEventToSalesManagers() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    CustomerOwnershipTriageOpenedEvent event =
        new CustomerOwnershipTriageOpenedEvent(
            tenantId, customerId, "Acme", Instant.parse("2026-07-28T10:00:00Z"), 3);
    SalesNotificationListener listener =
        new SalesNotificationListener(
            notificationHubService,
            departmentRecipientPort,
            permissionRecipientPort,
            idempotentEventHandler);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              invocation.getArgument(3, Runnable.class).run();
              return null;
            })
        .when(idempotentEventHandler)
        .executeOnce(
            eq(event.getEventId()),
            eq(SalesNotificationListener.class),
            eq("onCustomerOwnershipTriageOpened"),
            any(Runnable.class));
    when(departmentRecipientPort.findManagersByDepartmentKeyword(tenantId, "SALES"))
        .thenReturn(List.of(managerId));

    listener.onCustomerOwnershipTriageOpened(event);

    verify(notificationHubService)
        .notifyAll(
            List.of(managerId),
            tenantId,
            NotificationEventType.CUSTOMER_OWNERSHIP_TRIAGE_OPENED,
            Map.of(
                "customerName", "Acme",
                "gapStartedAt", "2026-07-28T10:00:00Z",
                "unassignedQuoteCount", "3"),
            customerId,
            "CUSTOMER");
  }

  @Test
  void escalatesAnOverdueCaseToEveryoneWhoCanAssignAnOwner() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID admin = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    UUID director = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    CustomerOwnershipTriageAgedEvent event =
        new CustomerOwnershipTriageAgedEvent(
            tenantId, customerId, "Acme", Instant.parse("2026-07-27T10:00:00Z"), 2, 26);
    SalesNotificationListener listener =
        new SalesNotificationListener(
            notificationHubService,
            departmentRecipientPort,
            permissionRecipientPort,
            idempotentEventHandler);
    runOnce(event.getEventId(), "onCustomerOwnershipTriageAged");
    when(permissionRecipientPort.findUsersWithPermission(
            tenantId, PermissionKey.SALES_ASSIGN_OWNER))
        .thenReturn(Set.of(director, admin));

    listener.onCustomerOwnershipTriageAged(event);

    verify(notificationHubService)
        .notifyAll(
            List.of(admin, director),
            tenantId,
            NotificationEventType.CUSTOMER_OWNERSHIP_TRIAGE_AGED,
            Map.of(
                "customerName", "Acme",
                "gapStartedAt", "2026-07-27T10:00:00Z",
                "ageHours", "26",
                "unassignedQuoteCount", "2"),
            customerId,
            "CUSTOMER");
    verifyNoInteractions(departmentRecipientPort);
  }

  @Test
  void anOverdueCaseWithNobodyAuthorisedNotifiesNobody() {
    UUID tenantId = UUID.randomUUID();
    CustomerOwnershipTriageAgedEvent event =
        new CustomerOwnershipTriageAgedEvent(
            tenantId, UUID.randomUUID(), "Acme", Instant.parse("2026-07-27T10:00:00Z"), 0, 30);
    SalesNotificationListener listener =
        new SalesNotificationListener(
            notificationHubService,
            departmentRecipientPort,
            permissionRecipientPort,
            idempotentEventHandler);
    runOnce(event.getEventId(), "onCustomerOwnershipTriageAged");
    when(permissionRecipientPort.findUsersWithPermission(
            tenantId, PermissionKey.SALES_ASSIGN_OWNER))
        .thenReturn(Set.of());

    listener.onCustomerOwnershipTriageAged(event);

    verify(notificationHubService, never()).notifyAll(any(), any(), any(), any(), any(), any());
  }

  private void runOnce(UUID eventId, String handler) {
    org.mockito.Mockito.doAnswer(
            invocation -> {
              invocation.getArgument(3, Runnable.class).run();
              return null;
            })
        .when(idempotentEventHandler)
        .executeOnce(
            eq(eventId), eq(SalesNotificationListener.class), eq(handler), any(Runnable.class));
  }
}
