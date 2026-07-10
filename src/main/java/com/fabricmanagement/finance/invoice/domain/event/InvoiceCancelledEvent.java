package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceCancelledEvent extends DomainEvent {

  private final UUID invoiceId;
  private final String invoiceNumber;

  public InvoiceCancelledEvent(UUID tenantId, UUID invoiceId, String invoiceNumber) {
    super(tenantId, "INVOICE_CANCELLED");
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
  }

  @JsonCreator
  public InvoiceCancelledEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("invoiceId") UUID invoiceId,
      @JsonProperty("invoiceNumber") String invoiceNumber) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "INVOICE_CANCELLED",
        occurredAt,
        correlationId);
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
  }
}
