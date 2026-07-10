package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Named seam for the order epic: order conversion consumes the converting quote's lot intents,
 * promotes them toward hard reservation, and may notify other active quotes that overlap the same
 * lots. QLINE-ATP-1 only defines the event contract; no order-side consumer is implemented here.
 */
@Getter
public class BatchLotQuantityIntentOrderConversionRequestedEvent extends DomainEvent {
  private final UUID quoteId;

  @JsonCreator
  public BatchLotQuantityIntentOrderConversionRequestedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("quoteId") UUID quoteId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_LOT_QUANTITY_INTENT_ORDER_CONVERSION_REQUESTED",
        occurredAt,
        correlationId);
    this.quoteId = quoteId;
  }

  public BatchLotQuantityIntentOrderConversionRequestedEvent(UUID tenantId, UUID quoteId) {
    super(tenantId, "BATCH_LOT_QUANTITY_INTENT_ORDER_CONVERSION_REQUESTED");
    this.quoteId = quoteId;
  }
}
