package com.fabricmanagement.platform.organization.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A contact was removed from an organization's card. Published inside the removing transaction, so
 * a synchronous listener can end whatever rested on that contact in the same commit.
 */
@Getter
public class OrganizationContactRemovedEvent extends DomainEvent {

  private final UUID organizationId;
  private final UUID contactId;

  public OrganizationContactRemovedEvent(UUID tenantId, UUID organizationId, UUID contactId) {
    super(tenantId, "ORGANIZATION_CONTACT_REMOVED");
    this.organizationId = organizationId;
    this.contactId = contactId;
  }

  @JsonCreator
  public OrganizationContactRemovedEvent(
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
        eventType != null ? eventType : "ORGANIZATION_CONTACT_REMOVED",
        occurredAt,
        correlationId);
    this.organizationId = organizationId;
    this.contactId = contactId;
  }
}
