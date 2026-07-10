package com.fabricmanagement.procurement.quote.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class SupplierQuoteAcceptedEvent extends DomainEvent {

  private final UUID quoteId;
  private final UUID rfqId;

  public SupplierQuoteAcceptedEvent(UUID tenantId, UUID quoteId, UUID rfqId) {
    super(tenantId, "SUPPLIER_QUOTE_ACCEPTED");
    this.quoteId = quoteId;
    this.rfqId = rfqId;
  }

  @JsonCreator
  public SupplierQuoteAcceptedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("quoteId") UUID quoteId,
      @JsonProperty("rfqId") UUID rfqId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SUPPLIER_QUOTE_ACCEPTED",
        occurredAt,
        correlationId);
    this.quoteId = quoteId;
    this.rfqId = rfqId;
  }
}
