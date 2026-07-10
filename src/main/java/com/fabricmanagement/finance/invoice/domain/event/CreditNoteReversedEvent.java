package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.util.Money;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class CreditNoteReversedEvent extends DomainEvent {
  private final UUID creditNoteId;
  private final UUID targetInvoiceId;
  private final Money amount;
  private final String targetNewPaymentStatus;

  public CreditNoteReversedEvent(
      UUID tenantId,
      UUID creditNoteId,
      UUID targetInvoiceId,
      Money amount,
      String targetNewPaymentStatus) {
    super(tenantId, "CREDIT_NOTE_REVERSED");
    this.creditNoteId = creditNoteId;
    this.targetInvoiceId = targetInvoiceId;
    this.amount = amount;
    this.targetNewPaymentStatus = targetNewPaymentStatus;
  }

  @JsonCreator
  public CreditNoteReversedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("creditNoteId") UUID creditNoteId,
      @JsonProperty("targetInvoiceId") UUID targetInvoiceId,
      @JsonProperty("amount") Money amount,
      @JsonProperty("targetNewPaymentStatus") String targetNewPaymentStatus) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "CREDIT_NOTE_REVERSED",
        occurredAt,
        correlationId);
    this.creditNoteId = creditNoteId;
    this.targetInvoiceId = targetInvoiceId;
    this.amount = amount;
    this.targetNewPaymentStatus = targetNewPaymentStatus;
  }
}
