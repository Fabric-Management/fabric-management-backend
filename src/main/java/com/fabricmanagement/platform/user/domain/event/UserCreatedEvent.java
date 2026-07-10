package com.fabricmanagement.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a new user is created.
 *
 * <p>Listeners: Audit, Analytics, Notification, Policy (cache warm-up)
 */
@Getter
public class UserCreatedEvent extends DomainEvent {

  private final UUID userId;
  private final String displayName;
  private final String contactValue;
  private final UUID organizationId;
  private final boolean invitationEmailSuppressed;

  public UserCreatedEvent(
      UUID tenantId,
      UUID userId,
      String displayName,
      String contactValue,
      UUID organizationId,
      boolean invitationEmailSuppressed) {
    super(tenantId, "USER_CREATED");
    this.userId = userId;
    this.displayName = displayName;
    this.contactValue = contactValue;
    this.organizationId = organizationId;
    this.invitationEmailSuppressed = invitationEmailSuppressed;
  }

  @JsonCreator
  public UserCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("displayName") String displayName,
      @JsonProperty("contactValue") String contactValue,
      @JsonProperty("organizationId") UUID organizationId,
      @JsonProperty("invitationEmailSuppressed") boolean invitationEmailSuppressed) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "USER_CREATED",
        occurredAt,
        correlationId);
    this.userId = userId;
    this.displayName = displayName;
    this.contactValue = contactValue;
    this.organizationId = organizationId;
    this.invitationEmailSuppressed = invitationEmailSuppressed;
  }
}
