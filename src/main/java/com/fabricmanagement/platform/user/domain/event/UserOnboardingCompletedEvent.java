package com.fabricmanagement.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a user completes onboarding.
 *
 * <p>Listeners: Analytics, Notifications, Audit
 */
@Getter
public class UserOnboardingCompletedEvent extends DomainEvent {

  private final UUID userId;

  public UserOnboardingCompletedEvent(UUID tenantId, UUID userId) {
    super(tenantId, "USER_ONBOARDING_COMPLETED");
    this.userId = userId;
  }

  @JsonCreator
  public UserOnboardingCompletedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "USER_ONBOARDING_COMPLETED",
        occurredAt,
        correlationId);
    this.userId = userId;
  }
}
