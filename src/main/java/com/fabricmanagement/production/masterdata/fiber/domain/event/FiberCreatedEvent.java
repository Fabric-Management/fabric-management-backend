package com.fabricmanagement.production.masterdata.fiber.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Domain event published when a new Fiber is created. */
@Getter
public class FiberCreatedEvent extends DomainEvent {

  private final UUID fiberId;
  private final String fiberName;
  private final UUID fiberCategoryId;
  private final UUID fiberIsoCodeId;

  @JsonCreator
  public FiberCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("fiberId") UUID fiberId,
      @JsonProperty("fiberName") String fiberName,
      @JsonProperty("fiberCategoryId") UUID fiberCategoryId,
      @JsonProperty("fiberIsoCodeId") UUID fiberIsoCodeId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "FIBER_CREATED",
        occurredAt,
        correlationId);
    this.fiberId = fiberId;
    this.fiberName = fiberName;
    this.fiberCategoryId = fiberCategoryId;
    this.fiberIsoCodeId = fiberIsoCodeId;
  }

  public FiberCreatedEvent(
      UUID tenantId, UUID fiberId, String fiberName, UUID fiberCategoryId, UUID fiberIsoCodeId) {
    super(tenantId, "FIBER_CREATED");
    this.fiberId = fiberId;
    this.fiberName = fiberName;
    this.fiberCategoryId = fiberCategoryId;
    this.fiberIsoCodeId = fiberIsoCodeId;
  }
}
