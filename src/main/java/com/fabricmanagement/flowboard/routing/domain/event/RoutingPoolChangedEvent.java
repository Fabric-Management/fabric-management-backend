package com.fabricmanagement.flowboard.routing.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class RoutingPoolChangedEvent extends DomainEvent {
  private final RoutingPoolKey poolKey;
  private final long revision;

  public RoutingPoolChangedEvent(UUID tenantId, RoutingPoolKey poolKey, long revision) {
    super(tenantId, "ROUTING_POOL_CHANGED");
    this.poolKey = poolKey;
    this.revision = revision;
  }

  @JsonCreator
  public RoutingPoolChangedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("poolKey") RoutingPoolKey poolKey,
      @JsonProperty("revision") long revision) {
    super(
        eventId,
        tenantId,
        eventType == null ? "ROUTING_POOL_CHANGED" : eventType,
        occurredAt,
        correlationId);
    this.poolKey = poolKey;
    this.revision = revision;
  }
}
