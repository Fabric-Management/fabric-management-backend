package com.fabricmanagement.sales.ownership.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class CustomerOwnershipTriageOpenedEvent extends DomainEvent {

  private static final String TYPE = "CUSTOMER_OWNERSHIP_TRIAGE_OPENED";

  private final UUID customerId;
  private final String customerName;
  private final Instant gapStartedAt;
  private final long unassignedOpenQuoteCount;

  public CustomerOwnershipTriageOpenedEvent(
      UUID tenantId,
      UUID customerId,
      String customerName,
      Instant gapStartedAt,
      long unassignedOpenQuoteCount) {
    super(tenantId, TYPE);
    this.customerId = customerId;
    this.customerName = customerName;
    this.gapStartedAt = gapStartedAt;
    this.unassignedOpenQuoteCount = unassignedOpenQuoteCount;
  }

  @JsonCreator
  public CustomerOwnershipTriageOpenedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("customerId") UUID customerId,
      @JsonProperty("customerName") String customerName,
      @JsonProperty("gapStartedAt") Instant gapStartedAt,
      @JsonProperty("unassignedOpenQuoteCount") long unassignedOpenQuoteCount) {
    super(eventId, tenantId, eventType != null ? eventType : TYPE, occurredAt, correlationId);
    this.customerId = customerId;
    this.customerName = customerName;
    this.gapStartedAt = gapStartedAt;
    this.unassignedOpenQuoteCount = unassignedOpenQuoteCount;
  }
}
