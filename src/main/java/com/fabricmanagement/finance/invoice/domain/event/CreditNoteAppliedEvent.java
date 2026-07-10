package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.util.Money;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class CreditNoteAppliedEvent extends DomainEvent {
  private final UUID creditNoteId;
  private final UUID targetInvoiceId;
  private final Money amount;
  private final String targetNewPaymentStatus;

  public CreditNoteAppliedEvent(
      UUID tenantId,
      UUID creditNoteId,
      UUID targetInvoiceId,
      Money amount,
      String targetNewPaymentStatus) {
    super(tenantId, "CREDIT_NOTE_APPLIED");
    this.creditNoteId = creditNoteId;
    this.targetInvoiceId = targetInvoiceId;
    this.amount = amount;
    this.targetNewPaymentStatus = targetNewPaymentStatus;
  }

  @JsonCreator
  public CreditNoteAppliedEvent(
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
        eventType != null ? eventType : "CREDIT_NOTE_APPLIED",
        occurredAt,
        correlationId);
    this.creditNoteId = creditNoteId;
    this.targetInvoiceId = targetInvoiceId;
    this.amount = amount;
    this.targetNewPaymentStatus = targetNewPaymentStatus;
  }
}
