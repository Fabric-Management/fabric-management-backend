package com.fabricmanagement.platform.organization.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Fired when a contact is assigned to an organization. */
@Getter
public class OrganizationContactAssignedEvent extends DomainEvent {

  private final UUID organizationId;
  private final UUID contactId;

  public OrganizationContactAssignedEvent(UUID tenantId, UUID organizationId, UUID contactId) {
    super(tenantId, "ORGANIZATION_CONTACT_ASSIGNED");
    this.organizationId = organizationId;
    this.contactId = contactId;
  }

  @JsonCreator
  public OrganizationContactAssignedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("organizationId") UUID organizationId,
      @JsonProperty("contactId") UUID contactId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "ORGANIZATION_CONTACT_ASSIGNED",
        occurredAt,
        correlationId);
    this.organizationId = organizationId;
    this.contactId = contactId;
  }
}
