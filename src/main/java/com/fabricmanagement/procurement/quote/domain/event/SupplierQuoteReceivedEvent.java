package com.fabricmanagement.procurement.quote.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Tedarikçi teklif verdi — NORMAL. */
@Getter
public class SupplierQuoteReceivedEvent extends DomainEvent {

  private final UUID quoteId;
  private final UUID rfqId;
  private final UUID supplierId;
  private final String supplierName;
  private final UUID rfqCreatedByUserId;

  @JsonCreator
  public SupplierQuoteReceivedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("quoteId") UUID quoteId,
      @JsonProperty("rfqId") UUID rfqId,
      @JsonProperty("supplierId") UUID supplierId,
      @JsonProperty("supplierName") String supplierName,
      @JsonProperty("rfqCreatedByUserId") UUID rfqCreatedByUserId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SUPPLIER_QUOTE_RECEIVED",
        occurredAt,
        correlationId);
    this.quoteId = quoteId;
    this.rfqId = rfqId;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
    this.rfqCreatedByUserId = rfqCreatedByUserId;
  }

  public SupplierQuoteReceivedEvent(
      UUID tenantId,
      UUID quoteId,
      UUID rfqId,
      UUID supplierId,
      String supplierName,
      UUID rfqCreatedByUserId) {
    super(tenantId, "SUPPLIER_QUOTE_RECEIVED");
    this.quoteId = quoteId;
    this.rfqId = rfqId;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
    this.rfqCreatedByUserId = rfqCreatedByUserId;
  }
}
