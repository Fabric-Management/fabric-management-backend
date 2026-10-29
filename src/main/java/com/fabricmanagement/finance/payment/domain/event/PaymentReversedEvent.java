package com.fabricmanagement.finance.payment.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/**
 * A payment was cancelled by a posted reversal. Not a {@link PaymentReceivedEvent}: a reversal is
 * not money arriving, so it must never raise a "payment received" notification. {@code
 * affectedInvoiceIds} are the invoices whose allocations the reversal released.
 */
@Getter
public class PaymentReversedEvent extends DomainEvent {

  private final UUID paymentId;
  private final String paymentNumber;
  private final UUID reversalPaymentId;
  private final String reversalPaymentNumber;
  private final UUID tradingPartnerId;
  private final PaymentDirection direction;
  private final Money amount;
  private final LocalDate reversalDate;
  private final List<UUID> affectedInvoiceIds;

  public PaymentReversedEvent(
      UUID tenantId,
      UUID paymentId,
      String paymentNumber,
      UUID reversalPaymentId,
      String reversalPaymentNumber,
      UUID tradingPartnerId,
      PaymentDirection direction,
      Money amount,
      LocalDate reversalDate,
      List<UUID> affectedInvoiceIds) {
    super(tenantId, "PAYMENT_REVERSED");
    this.paymentId = paymentId;
    this.paymentNumber = paymentNumber;
    this.reversalPaymentId = reversalPaymentId;
    this.reversalPaymentNumber = reversalPaymentNumber;
    this.tradingPartnerId = tradingPartnerId;
    this.direction = direction;
    this.amount = amount;
    this.reversalDate = reversalDate;
    this.affectedInvoiceIds = affectedInvoiceIds;
  }

  @JsonCreator
  public PaymentReversedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("paymentId") UUID paymentId,
      @JsonProperty("paymentNumber") String paymentNumber,
      @JsonProperty("reversalPaymentId") UUID reversalPaymentId,
      @JsonProperty("reversalPaymentNumber") String reversalPaymentNumber,
      @JsonProperty("tradingPartnerId") UUID tradingPartnerId,
      @JsonProperty("direction") PaymentDirection direction,
      @JsonProperty("amount") Money amount,
      @JsonProperty("reversalDate") LocalDate reversalDate,
      @JsonProperty("affectedInvoiceIds") List<UUID> affectedInvoiceIds) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PAYMENT_REVERSED",
        occurredAt,
        correlationId);
    this.paymentId = paymentId;
    this.paymentNumber = paymentNumber;
    this.reversalPaymentId = reversalPaymentId;
    this.reversalPaymentNumber = reversalPaymentNumber;
    this.tradingPartnerId = tradingPartnerId;
    this.direction = direction;
    this.amount = amount;
    this.reversalDate = reversalDate;
    this.affectedInvoiceIds = affectedInvoiceIds;
  }
}
