package com.fabricmanagement.finance.payment.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public class PaymentVoidedEvent extends DomainEvent {

  private final UUID paymentId;
  private final String paymentNumber;
  private final List<UUID> affectedInvoiceIds;

  public PaymentVoidedEvent(
      UUID tenantId, UUID paymentId, String paymentNumber, List<UUID> affectedInvoiceIds) {
    super(tenantId, "PAYMENT_VOIDED");
    this.paymentId = paymentId;
    this.paymentNumber = paymentNumber;
    this.affectedInvoiceIds = affectedInvoiceIds;
  }

  @JsonCreator
  public PaymentVoidedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("paymentId") UUID paymentId,
      @JsonProperty("paymentNumber") String paymentNumber,
      @JsonProperty("affectedInvoiceIds") List<UUID> affectedInvoiceIds) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PAYMENT_VOIDED",
        occurredAt,
        correlationId);
    this.paymentId = paymentId;
    this.paymentNumber = paymentNumber;
    this.affectedInvoiceIds = affectedInvoiceIds;
  }
}
