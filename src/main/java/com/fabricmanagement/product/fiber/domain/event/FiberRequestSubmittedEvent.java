package com.fabricmanagement.product.fiber.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a tenant asks for a fibre to be added to the shared catalogue; the platform
 * administrators review it.
 */
@Getter
public class FiberRequestSubmittedEvent extends DomainEvent {

  private final UUID requestId;
  private final UUID requestedBy;
  private final String isoCode;
  private final String fiberName;
  private final String tenantName;

  public FiberRequestSubmittedEvent(
      UUID tenantId,
      UUID requestId,
      UUID requestedBy,
      String isoCode,
      String fiberName,
      String tenantName) {
    super(tenantId, "FIBER_REQUEST_SUBMITTED");
    this.requestId = requestId;
    this.requestedBy = requestedBy;
    this.isoCode = isoCode;
    this.fiberName = fiberName;
    this.tenantName = tenantName;
  }

  @JsonCreator
  public FiberRequestSubmittedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("requestId") UUID requestId,
      @JsonProperty("requestedBy") UUID requestedBy,
      @JsonProperty("isoCode") String isoCode,
      @JsonProperty("fiberName") String fiberName,
      @JsonProperty("tenantName") String tenantName) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "FIBER_REQUEST_SUBMITTED",
        occurredAt,
        correlationId);
    this.requestId = requestId;
    this.requestedBy = requestedBy;
    this.isoCode = isoCode;
    this.fiberName = fiberName;
    this.tenantName = tenantName;
  }
}
