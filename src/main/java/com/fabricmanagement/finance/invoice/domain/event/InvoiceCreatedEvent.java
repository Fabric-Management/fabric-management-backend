package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceCreatedEvent extends DomainEvent {

  private final UUID invoiceId;
  private final UUID tradingPartnerId;
  private final String invoiceNumber;
  private final String invoiceType;
  private final BigDecimal totalAmount;
  private final String currency;

  public InvoiceCreatedEvent(
      UUID tenantId,
      UUID invoiceId,
      UUID tradingPartnerId,
      String invoiceNumber,
      String invoiceType,
      BigDecimal totalAmount,
      String currency) {
    super(tenantId, "INVOICE_CREATED");
    this.invoiceId = invoiceId;
    this.tradingPartnerId = tradingPartnerId;
    this.invoiceNumber = invoiceNumber;
    this.invoiceType = invoiceType;
    this.totalAmount = totalAmount;
    this.currency = currency;
  }

  @JsonCreator
  public InvoiceCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("invoiceId") UUID invoiceId,
      @JsonProperty("tradingPartnerId") UUID tradingPartnerId,
      @JsonProperty("invoiceNumber") String invoiceNumber,
      @JsonProperty("invoiceType") String invoiceType,
      @JsonProperty("totalAmount") BigDecimal totalAmount,
      @JsonProperty("currency") String currency) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "INVOICE_CREATED",
        occurredAt,
        correlationId);
    this.invoiceId = invoiceId;
    this.tradingPartnerId = tradingPartnerId;
    this.invoiceNumber = invoiceNumber;
    this.invoiceType = invoiceType;
    this.totalAmount = totalAmount;
    this.currency = currency;
  }
}
