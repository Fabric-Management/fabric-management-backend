package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchLotQuantityIntentPlacedEvent extends DomainEvent {
  private final UUID quoteId;
  private final UUID quoteLineId;
  private final UUID batchId;
  private final UUID intentId;

  @JsonCreator
  public BatchLotQuantityIntentPlacedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("quoteId") UUID quoteId,
      @JsonProperty("quoteLineId") UUID quoteLineId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("intentId") UUID intentId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_LOT_QUANTITY_INTENT_PLACED",
        occurredAt,
        correlationId);
    this.quoteId = quoteId;
    this.quoteLineId = quoteLineId;
    this.batchId = batchId;
    this.intentId = intentId;
  }

  public BatchLotQuantityIntentPlacedEvent(
      UUID tenantId, UUID quoteId, UUID quoteLineId, UUID batchId, UUID intentId) {
    super(tenantId, "BATCH_LOT_QUANTITY_INTENT_PLACED");
    this.quoteId = quoteId;
    this.quoteLineId = quoteLineId;
    this.batchId = batchId;
    this.intentId = intentId;
  }
}
