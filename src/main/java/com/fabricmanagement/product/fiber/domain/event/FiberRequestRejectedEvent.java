package com.fabricmanagement.product.fiber.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a platform administrator rejects a tenant's fibre request. The event's tenant is
 * the requesting tenant.
 */
@Getter
public class FiberRequestRejectedEvent extends DomainEvent {

  private final UUID requestId;
  private final UUID requestedBy;
  private final String isoCode;
  private final String fiberName;
  private final String reviewNote;

  public FiberRequestRejectedEvent(
      UUID tenantId,
      UUID requestId,
      UUID requestedBy,
      String isoCode,
      String fiberName,
      String reviewNote) {
    super(tenantId, "FIBER_REQUEST_REJECTED");
    this.requestId = requestId;
    this.requestedBy = requestedBy;
    this.isoCode = isoCode;
    this.fiberName = fiberName;
    this.reviewNote = reviewNote;
  }

  @JsonCreator
  public FiberRequestRejectedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("requestId") UUID requestId,
      @JsonProperty("requestedBy") UUID requestedBy,
      @JsonProperty("isoCode") String isoCode,
      @JsonProperty("fiberName") String fiberName,
      @JsonProperty("reviewNote") String reviewNote) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "FIBER_REQUEST_REJECTED",
        occurredAt,
        correlationId);
    this.requestId = requestId;
    this.requestedBy = requestedBy;
    this.isoCode = isoCode;
    this.fiberName = fiberName;
    this.reviewNote = reviewNote;
  }
}
