package com.fabricmanagement.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when user logs out.
 *
 * <p>Listeners: Audit, Analytics, Monitoring (track session end)
 */
@Getter
public class UserLogoutEvent extends DomainEvent {

  private final UUID userId;

  public UserLogoutEvent(UUID tenantId, UUID userId) {
    super(tenantId, "USER_LOGOUT");
    this.userId = userId;
  }

  @JsonCreator
  public UserLogoutEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "USER_LOGOUT",
        occurredAt,
        correlationId);
    this.userId = userId;
  }
}
