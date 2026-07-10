package com.fabricmanagement.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.platform.user.domain.value.ProfileCategory;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when user profile is updated.
 *
 * <p>Listeners: Audit, Analytics, Notifications
 */
@Getter
public class UserProfileUpdatedEvent extends DomainEvent {

  private final UUID userId;
  private final UUID updatedBy;
  private final Set<ProfileCategory> categories;

  public UserProfileUpdatedEvent(
      UUID tenantId, UUID userId, UUID updatedBy, Set<ProfileCategory> categories) {
    super(tenantId, "USER_PROFILE_UPDATED");
    this.userId = userId;
    this.updatedBy = updatedBy;
    this.categories = categories;
  }

  @JsonCreator
  public UserProfileUpdatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("updatedBy") UUID updatedBy,
      @JsonProperty("categories") Set<ProfileCategory> categories) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "USER_PROFILE_UPDATED",
        occurredAt,
        correlationId);
    this.userId = userId;
    this.updatedBy = updatedBy;
    this.categories = categories;
  }
}
