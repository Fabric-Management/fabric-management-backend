package com.fabricmanagement.sales.ownership.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class CustomerOwnershipResolvedEvent extends DomainEvent {

  private static final String TYPE = "CUSTOMER_OWNERSHIP_RESOLVED";

  private final UUID customerId;
  private final UUID representativeId;
  private final Instant resolvedAt;

  public CustomerOwnershipResolvedEvent(
      UUID tenantId, UUID customerId, UUID representativeId, Instant resolvedAt) {
    super(tenantId, TYPE);
    this.customerId = customerId;
    this.representativeId = representativeId;
    this.resolvedAt = resolvedAt;
  }

  @JsonCreator
  public CustomerOwnershipResolvedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("customerId") UUID customerId,
      @JsonProperty("representativeId") UUID representativeId,
      @JsonProperty("resolvedAt") Instant resolvedAt) {
    super(eventId, tenantId, eventType != null ? eventType : TYPE, occurredAt, correlationId);
    this.customerId = customerId;
    this.representativeId = representativeId;
    this.resolvedAt = resolvedAt;
  }
}
