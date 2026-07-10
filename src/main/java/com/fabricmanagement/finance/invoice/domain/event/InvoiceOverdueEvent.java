package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceOverdueEvent extends DomainEvent {

  private final UUID invoiceId;
  private final String invoiceNumber;
  private final UUID tradingPartnerId;
  private final long daysOverdue;

  public InvoiceOverdueEvent(
      UUID tenantId,
      UUID invoiceId,
      String invoiceNumber,
      UUID tradingPartnerId,
      long daysOverdue) {
    super(tenantId, "INVOICE_OVERDUE");
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.tradingPartnerId = tradingPartnerId;
    this.daysOverdue = daysOverdue;
  }

  @JsonCreator
  public InvoiceOverdueEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("invoiceId") UUID invoiceId,
      @JsonProperty("invoiceNumber") String invoiceNumber,
      @JsonProperty("tradingPartnerId") UUID tradingPartnerId,
      @JsonProperty("daysOverdue") long daysOverdue) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "INVOICE_OVERDUE",
        occurredAt,
        correlationId);
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.tradingPartnerId = tradingPartnerId;
    this.daysOverdue = daysOverdue;
  }
}
