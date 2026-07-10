package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceSentEvent extends DomainEvent {

  private final UUID invoiceId;
  private final String invoiceNumber;
  private final UUID tradingPartnerId;

  public InvoiceSentEvent(
      UUID tenantId, UUID invoiceId, String invoiceNumber, UUID tradingPartnerId) {
    super(tenantId, "INVOICE_SENT");
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.tradingPartnerId = tradingPartnerId;
  }

  @JsonCreator
  public InvoiceSentEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("invoiceId") UUID invoiceId,
      @JsonProperty("invoiceNumber") String invoiceNumber,
      @JsonProperty("tradingPartnerId") UUID tradingPartnerId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "INVOICE_SENT",
        occurredAt,
        correlationId);
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.tradingPartnerId = tradingPartnerId;
  }
}
