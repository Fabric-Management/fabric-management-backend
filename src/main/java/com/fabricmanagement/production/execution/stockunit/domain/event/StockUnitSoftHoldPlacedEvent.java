package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class StockUnitSoftHoldPlacedEvent extends DomainEvent {
  private final UUID quoteLineId;
  private final UUID stockUnitId;
  private final UUID softHoldId;

  @JsonCreator
  public StockUnitSoftHoldPlacedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("quoteLineId") UUID quoteLineId,
      @JsonProperty("stockUnitId") UUID stockUnitId,
      @JsonProperty("softHoldId") UUID softHoldId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "STOCK_UNIT_SOFT_HOLD_PLACED",
        occurredAt,
        correlationId);
    this.quoteLineId = quoteLineId;
    this.stockUnitId = stockUnitId;
    this.softHoldId = softHoldId;
  }

  public StockUnitSoftHoldPlacedEvent(
      UUID tenantId, UUID quoteLineId, UUID stockUnitId, UUID softHoldId) {
    super(tenantId, "STOCK_UNIT_SOFT_HOLD_PLACED");
    this.quoteLineId = quoteLineId;
    this.stockUnitId = stockUnitId;
    this.softHoldId = softHoldId;
  }
}
