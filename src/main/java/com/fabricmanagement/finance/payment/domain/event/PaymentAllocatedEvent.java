package com.fabricmanagement.finance.payment.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.util.Money;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class PaymentAllocatedEvent extends DomainEvent {

  private final UUID paymentId;
  private final UUID invoiceId;
  private final Money allocationAmount;

  public PaymentAllocatedEvent(
      UUID tenantId, UUID paymentId, UUID invoiceId, Money allocationAmount) {
    super(tenantId, "PAYMENT_ALLOCATED");
    this.paymentId = paymentId;
    this.invoiceId = invoiceId;
    this.allocationAmount = allocationAmount;
  }

  @JsonCreator
  public PaymentAllocatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("paymentId") UUID paymentId,
      @JsonProperty("invoiceId") UUID invoiceId,
      @JsonProperty("allocationAmount") Money allocationAmount) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PAYMENT_ALLOCATED",
        occurredAt,
        correlationId);
    this.paymentId = paymentId;
    this.invoiceId = invoiceId;
    this.allocationAmount = allocationAmount;
  }
}
