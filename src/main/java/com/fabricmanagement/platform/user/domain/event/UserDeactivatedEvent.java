package com.fabricmanagement.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a user is deactivated.
 *
 * <p>CRITICAL: Must invalidate policy cache and revoke active sessions!
 *
 * <p>Listeners: Policy (invalidate cache), Auth (revoke tokens), Audit
 */
@Getter
public class UserDeactivatedEvent extends DomainEvent {

  private final UUID userId;
  private final String reason;

  public UserDeactivatedEvent(UUID tenantId, UUID userId, String reason) {
    super(tenantId, "USER_DEACTIVATED");
    this.userId = userId;
    this.reason = reason;
  }

  @JsonCreator
  public UserDeactivatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("reason") String reason) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "USER_DEACTIVATED",
        occurredAt,
        correlationId);
    this.userId = userId;
    this.reason = reason;
  }
}
