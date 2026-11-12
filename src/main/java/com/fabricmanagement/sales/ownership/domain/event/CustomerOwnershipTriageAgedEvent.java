package com.fabricmanagement.sales.ownership.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * An ownership triage case outlived the tenant's threshold without anyone assigning an owner.
 *
 * <p>The escalation stays inside the tenant: the notification module routes it to the tenant's own
 * users who can resolve it. Nothing about the case leaves the tenant.
 */
@Getter
public class CustomerOwnershipTriageAgedEvent extends DomainEvent {

  private static final String TYPE = "CUSTOMER_OWNERSHIP_TRIAGE_AGED";

  private final UUID customerId;
  private final String customerName;
  private final Instant gapStartedAt;
  private final long unassignedOpenQuoteCount;
  private final long ageHours;

  public CustomerOwnershipTriageAgedEvent(
      UUID tenantId,
      UUID customerId,
      String customerName,
      Instant gapStartedAt,
      long unassignedOpenQuoteCount,
      long ageHours) {
    super(tenantId, TYPE);
    this.customerId = customerId;
    this.customerName = customerName;
    this.gapStartedAt = gapStartedAt;
    this.unassignedOpenQuoteCount = unassignedOpenQuoteCount;
    this.ageHours = ageHours;
  }

  @JsonCreator
  public CustomerOwnershipTriageAgedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("customerId") UUID customerId,
      @JsonProperty("customerName") String customerName,
      @JsonProperty("gapStartedAt") Instant gapStartedAt,
      @JsonProperty("unassignedOpenQuoteCount") long unassignedOpenQuoteCount,
      @JsonProperty("ageHours") long ageHours) {
    super(eventId, tenantId, eventType != null ? eventType : TYPE, occurredAt, correlationId);
    this.customerId = customerId;
    this.customerName = customerName;
    this.gapStartedAt = gapStartedAt;
    this.unassignedOpenQuoteCount = unassignedOpenQuoteCount;
    this.ageHours = ageHours;
  }
}
