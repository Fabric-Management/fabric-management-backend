package com.fabricmanagement.platform.organization.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Domain event published when an organization is created.
 *
 * <p>Listeners: Audit, Analytics modules
 */
@Getter
public class OrganizationCreatedEvent extends DomainEvent {

  private final UUID organizationId;
  private final String name;
  private final OrganizationType organizationType;

  public OrganizationCreatedEvent(
      UUID tenantId, UUID organizationId, String name, OrganizationType organizationType) {
    super(tenantId, "ORGANIZATION_CREATED");
    this.organizationId = organizationId;
    this.name = name;
    this.organizationType = organizationType;
  }

  @JsonCreator
  public OrganizationCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("organizationId") UUID organizationId,
      @JsonProperty("name") String name,
      @JsonProperty("organizationType") OrganizationType organizationType) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "ORGANIZATION_CREATED",
        occurredAt,
        correlationId);
    this.organizationId = organizationId;
    this.name = name;
    this.organizationType = organizationType;
  }
}
