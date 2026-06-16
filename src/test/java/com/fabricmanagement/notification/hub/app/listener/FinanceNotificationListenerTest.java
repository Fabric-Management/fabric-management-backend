package com.fabricmanagement.notification.hub.app.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.events.ProcessedEventRepository;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.invoice.domain.event.InvoiceDisputedEvent;
import com.fabricmanagement.finance.invoice.domain.event.InvoiceOverdueEvent;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.event.PaymentReceivedEvent;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceNotificationListenerTest {

  @Mock private NotificationHubService notificationHubService;
  @Mock private DepartmentRecipientPort departmentRecipientPort;
  @Mock private ProcessedEventRepository processedEventRepository;

  private IdempotentEventHandler idempotentHandler;
  private FinanceNotificationListener listener;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    idempotentHandler =
        new IdempotentEventHandler(processedEventRepository, new SimpleMeterRegistry());
    listener =
        new FinanceNotificationListener(
            notificationHubService, departmentRecipientPort, idempotentHandler);
  }

  @Test
  void onInvoiceOverdue_whenNotProcessed_sendsNotification() {
    InvoiceOverdueEvent event =
        new InvoiceOverdueEvent(tenantId, UUID.randomUUID(), "INV-01", UUID.randomUUID(), 10);

    when(processedEventRepository.tryInsert(eq(event.getEventId()), any())).thenReturn(1);
    when(departmentRecipientPort.findManagersByDepartmentKeyword(tenantId, "FINANCE", "MANAGEMENT"))
        .thenReturn(List.of(UUID.randomUUID()));

    listener.onInvoiceOverdue(event);

    verify(notificationHubService, times(1))
        .notifyAll(
            any(),
            eq(tenantId),
            eq(NotificationEventType.INVOICE_OVERDUE),
            any(),
            eq(event.getInvoiceId()),
            eq("INVOICE"));
  }

  @Test
  void onInvoiceOverdue_whenRedelivered_dedupesAndIgnores() {
    InvoiceOverdueEvent event =
        new InvoiceOverdueEvent(tenantId, UUID.randomUUID(), "INV-01", UUID.randomUUID(), 10);

    // Simulate first delivery success
    when(processedEventRepository.tryInsert(eq(event.getEventId()), any()))
        .thenReturn(1)
        .thenReturn(0);
    when(departmentRecipientPort.findManagersByDepartmentKeyword(tenantId, "FINANCE", "MANAGEMENT"))
        .thenReturn(List.of(UUID.randomUUID()));

    // Delivery 1
    listener.onInvoiceOverdue(event);
    // Delivery 2 (Redelivery)
    listener.onInvoiceOverdue(event);

    // Assert it was only sent ONCE
    verify(notificationHubService, times(1)).notifyAll(any(), any(), any(), any(), any(), any());
  }

  @Test
  void onPaymentReceived_whenUsersFound_sendsNotification() {
    PaymentReceivedEvent event =
        new PaymentReceivedEvent(
            tenantId,
            UUID.randomUUID(),
            "PAY-01",
            UUID.randomUUID(),
            PaymentDirection.INBOUND,
            Money.of(100, "USD"),
            "USD");

    when(processedEventRepository.tryInsert(eq(event.getEventId()), any())).thenReturn(1);
    when(departmentRecipientPort.findUsersByDepartmentKeyword(tenantId, "FINANCE", "SALES"))
        .thenReturn(List.of(UUID.randomUUID()));

    listener.onPaymentReceived(event);

    verify(notificationHubService, times(1))
        .notifyAll(
            any(),
            eq(tenantId),
            eq(NotificationEventType.PAYMENT_RECEIVED),
            any(),
            eq(event.getPaymentId()),
            eq("PAYMENT"));
  }

  @Test
  void onInvoiceDisputed_dedupeLogic() {
    InvoiceDisputedEvent event =
        new InvoiceDisputedEvent(tenantId, UUID.randomUUID(), "INV-02", UUID.randomUUID());

    when(processedEventRepository.tryInsert(eq(event.getEventId()), any()))
        .thenReturn(1)
        .thenReturn(0);
    when(departmentRecipientPort.findUsersByDepartmentKeyword(tenantId, "FINANCE", "SALES"))
        .thenReturn(List.of(UUID.randomUUID()));

    listener.onInvoiceDisputed(event);
    listener.onInvoiceDisputed(event);

    verify(notificationHubService, times(1)).notifyAll(any(), any(), any(), any(), any(), any());
  }
}
