package com.fabricmanagement.product.fiber.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a platform administrator approves a tenant's fibre request. The event's tenant is
 * the requesting tenant.
 */
@Getter
public class FiberRequestApprovedEvent extends DomainEvent {

  private final UUID requestId;
  private final UUID requestedBy;
  private final String isoCode;
  private final String fiberName;

  public FiberRequestApprovedEvent(
      UUID tenantId, UUID requestId, UUID requestedBy, String isoCode, String fiberName) {
    super(tenantId, "FIBER_REQUEST_APPROVED");
    this.requestId = requestId;
    this.requestedBy = requestedBy;
    this.isoCode = isoCode;
    this.fiberName = fiberName;
  }

  @JsonCreator
  public FiberRequestApprovedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("requestId") UUID requestId,
      @JsonProperty("requestedBy") UUID requestedBy,
      @JsonProperty("isoCode") String isoCode,
      @JsonProperty("fiberName") String fiberName) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "FIBER_REQUEST_APPROVED",
        occurredAt,
        correlationId);
    this.requestId = requestId;
    this.requestedBy = requestedBy;
    this.isoCode = isoCode;
    this.fiberName = fiberName;
  }
}
