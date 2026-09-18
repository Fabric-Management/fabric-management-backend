package com.fabricmanagement.flowboard.routing.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class RoutingFailureOpenedEvent extends DomainEvent {
  private final UUID failureId;
  private final UUID taskId;

  public RoutingFailureOpenedEvent(UUID tenantId, UUID failureId, UUID taskId) {
    super(tenantId, "ROUTING_FAILURE_OPENED");
    this.failureId = failureId;
    this.taskId = taskId;
  }

  @JsonCreator
  public RoutingFailureOpenedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("failureId") UUID failureId,
      @JsonProperty("taskId") UUID taskId) {
    super(
        eventId,
        tenantId,
        eventType == null ? "ROUTING_FAILURE_OPENED" : eventType,
        occurredAt,
        correlationId);
    this.failureId = failureId;
    this.taskId = taskId;
  }
}
