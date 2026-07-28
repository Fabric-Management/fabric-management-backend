package com.fabricmanagement.platform.tradingpartner.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/** Published when a tenant first establishes a customer relationship. */
@Getter
public class CustomerRelationshipEstablishedEvent extends DomainEvent {

  private final UUID customerId;
  private final UUID acquiredById;
  private final Instant establishedAt;
  private final CustomerRelationshipSourceGate sourceGate;

  public CustomerRelationshipEstablishedEvent(
      UUID tenantId,
      UUID customerId,
      UUID acquiredById,
      Instant establishedAt,
      CustomerRelationshipSourceGate sourceGate) {
    super(tenantId, "CUSTOMER_RELATIONSHIP_ESTABLISHED");
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    this.acquiredById = acquiredById;
    this.establishedAt = Objects.requireNonNull(establishedAt, "establishedAt must not be null");
    this.sourceGate = Objects.requireNonNull(sourceGate, "sourceGate must not be null");
  }

  @JsonCreator
  public CustomerRelationshipEstablishedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("customerId") UUID customerId,
      @JsonProperty("acquiredById") UUID acquiredById,
      @JsonProperty("establishedAt") Instant establishedAt,
      @JsonProperty("sourceGate") CustomerRelationshipSourceGate sourceGate) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "CUSTOMER_RELATIONSHIP_ESTABLISHED",
        occurredAt,
        correlationId);
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    this.acquiredById = acquiredById;
    this.establishedAt = Objects.requireNonNull(establishedAt, "establishedAt must not be null");
    this.sourceGate = Objects.requireNonNull(sourceGate, "sourceGate must not be null");
  }
}
