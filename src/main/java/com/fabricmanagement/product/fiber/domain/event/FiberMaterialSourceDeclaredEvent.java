package com.fabricmanagement.product.fiber.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Published when a previously undeclared pure fiber receives its material-source declaration. */
@Getter
public class FiberMaterialSourceDeclaredEvent extends DomainEvent {

  private final UUID fiberId;
  private final MaterialSource oldValue;
  private final MaterialSource newValue;
  private final UUID actorId;

  public FiberMaterialSourceDeclaredEvent(
      UUID tenantId, UUID fiberId, MaterialSource oldValue, MaterialSource newValue, UUID actorId) {
    super(tenantId, "FIBER_MATERIAL_SOURCE_DECLARED");
    this.fiberId = fiberId;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.actorId = actorId;
  }

  @JsonCreator
  public FiberMaterialSourceDeclaredEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("fiberId") UUID fiberId,
      @JsonProperty("oldValue") MaterialSource oldValue,
      @JsonProperty("newValue") MaterialSource newValue,
      @JsonProperty("actorId") UUID actorId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "FIBER_MATERIAL_SOURCE_DECLARED",
        occurredAt,
        correlationId);
    this.fiberId = fiberId;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.actorId = actorId;
  }
}
