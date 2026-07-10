package com.fabricmanagement.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Fired when a contact is assigned to a user. Communication module may listen (e.g. send code). */
@Getter
public class ContactAssignedEvent extends DomainEvent {

  private final UUID userId;
  private final UUID contactId;
  private final String ownerType;

  public ContactAssignedEvent(UUID tenantId, UUID userId, UUID contactId) {
    super(tenantId, "CONTACT_ASSIGNED");
    this.userId = userId;
    this.contactId = contactId;
    this.ownerType = "USER";
  }

  @JsonCreator
  public ContactAssignedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("contactId") UUID contactId,
      @JsonProperty("ownerType") String ownerType) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "CONTACT_ASSIGNED",
        occurredAt,
        correlationId);
    this.userId = userId;
    this.contactId = contactId;
    this.ownerType = ownerType != null ? ownerType : "USER";
  }
}
