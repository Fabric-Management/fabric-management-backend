package com.fabricmanagement.notification.hub.app.listener;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoPartiallyReceivedEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcurementNotificationListenerTest {

  @Mock private NotificationHubService notificationHubService;
  @Mock private DepartmentRecipientPort departmentRecipientPort;

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void partialReceiptUsesTemplatePlaceholderNames() {
    UUID tenantId = UUID.randomUUID();
    UUID purchaseOrderId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();
    when(departmentRecipientPort.findManagersByDepartmentKeyword(tenantId, "WAREHOUSE"))
        .thenReturn(List.of(recipientId));
    var listener =
        new ProcurementNotificationListener(notificationHubService, departmentRecipientPort);

    listener.onPoPartiallyReceived(
        new PoPartiallyReceivedEvent(tenantId, purchaseOrderId, "PO-20260723-00001", 2, 4));

    verify(notificationHubService)
        .notifyAll(
            eq(List.of(recipientId)),
            eq(tenantId),
            eq(NotificationEventType.PO_PARTIALLY_RECEIVED),
            eq(
                Map.of(
                    "poNumber", "PO-20260723-00001",
                    "receivedItemCount", "2",
                    "totalItemCount", "4")),
            eq(purchaseOrderId),
            eq("PO"));
  }
}
