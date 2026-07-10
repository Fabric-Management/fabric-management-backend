package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceIssuedEvent extends DomainEvent {

  private final UUID invoiceId;
  private final String invoiceNumber;
  private final BigDecimal totalAmount;

  public InvoiceIssuedEvent(
      UUID tenantId, UUID invoiceId, String invoiceNumber, BigDecimal totalAmount) {
    super(tenantId, "INVOICE_ISSUED");
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.totalAmount = totalAmount;
  }

  @JsonCreator
  public InvoiceIssuedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("invoiceId") UUID invoiceId,
      @JsonProperty("invoiceNumber") String invoiceNumber,
      @JsonProperty("totalAmount") BigDecimal totalAmount) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "INVOICE_ISSUED",
        occurredAt,
        correlationId);
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.totalAmount = totalAmount;
  }
}
