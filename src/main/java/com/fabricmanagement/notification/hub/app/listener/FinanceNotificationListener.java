package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.finance.invoice.domain.event.InvoiceDisputedEvent;
import com.fabricmanagement.finance.invoice.domain.event.InvoiceOverdueEvent;
import com.fabricmanagement.finance.payment.domain.event.PaymentReceivedEvent;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens for critical finance events (published via Modulith) and forwards them to
 * NotificationHub. Uses IdempotentEventHandler to ensure exactly-once notification delivery even if
 * the event is re-delivered after a crash (republish-outstanding-events-on-restart: true).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinanceNotificationListener {

  private final NotificationHubService notificationHubService;
  private final DepartmentRecipientPort departmentRecipientPort;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void onInvoiceOverdue(InvoiceOverdueEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onInvoiceOverdue",
        () -> {
          log.info("Handling InvoiceOverdueEvent for invoice {}", event.getInvoiceNumber());
          List<UUID> managers =
              departmentRecipientPort.findManagersByDepartmentKeyword(
                  event.getTenantId(), "FINANCE", "MANAGEMENT");

          if (managers.isEmpty()) return;

          notificationHubService.notifyAll(
              managers,
              event.getTenantId(),
              NotificationEventType.INVOICE_OVERDUE,
              Map.of(
                  "invoiceNumber", event.getInvoiceNumber(),
                  "daysOverdue", String.valueOf(event.getDaysOverdue()),
                  "tradingPartnerId", event.getTradingPartnerId().toString()),
              event.getInvoiceId(),
              "INVOICE");
        });
  }

  @ApplicationModuleListener
  public void onPaymentReceived(PaymentReceivedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onPaymentReceived",
        () -> {
          log.info("Handling PaymentReceivedEvent for payment {}", event.getPaymentNumber());
          List<UUID> users =
              departmentRecipientPort.findUsersByDepartmentKeyword(
                  event.getTenantId(), "FINANCE", "SALES");

          if (users.isEmpty()) return;

          notificationHubService.notifyAll(
              users,
              event.getTenantId(),
              NotificationEventType.PAYMENT_RECEIVED,
              Map.of(
                  "paymentNumber", event.getPaymentNumber(),
                  "amount", event.getAmount().getAmount().toString(),
                  "currency", event.getCurrency(),
                  "direction", event.getDirection().name(),
                  "tradingPartnerId", event.getTradingPartnerId().toString()),
              event.getPaymentId(),
              "PAYMENT");
        });
  }

  @ApplicationModuleListener
  public void onInvoiceDisputed(InvoiceDisputedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onInvoiceDisputed",
        () -> {
          log.info("Handling InvoiceDisputedEvent for invoice {}", event.getInvoiceNumber());
          List<UUID> users =
              departmentRecipientPort.findUsersByDepartmentKeyword(
                  event.getTenantId(), "FINANCE", "SALES");

          if (users.isEmpty()) return;

          notificationHubService.notifyAll(
              users,
              event.getTenantId(),
              NotificationEventType.INVOICE_DISPUTED,
              Map.of(
                  "invoiceNumber", event.getInvoiceNumber(),
                  "tradingPartnerId", event.getTradingPartnerId().toString()),
              event.getInvoiceId(),
              "INVOICE");
        });
  }
}
