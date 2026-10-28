package com.fabricmanagement.sales.orderintake.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Invalidation only: the task owner reads current committed planning needs, never stale facts. */
@Getter
public class SalesStockPlanningChangedEvent extends DomainEvent {
  private final UUID documentId;
  private final boolean quote;

  public SalesStockPlanningChangedEvent(UUID tenantId, UUID documentId, boolean quote) {
    super(tenantId, "SalesStockPlanningChanged");
    this.documentId = documentId;
    this.quote = quote;
  }

  @JsonCreator
  public SalesStockPlanningChangedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("documentId") UUID documentId,
      @JsonProperty("quote") boolean quote) {
    super(eventId, tenantId, "SalesStockPlanningChanged", occurredAt, correlationId);
    this.documentId = documentId;
    this.quote = quote;
  }
}
