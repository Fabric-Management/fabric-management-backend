package com.fabricmanagement.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when user successfully logs in.
 *
 * <p>Listeners: Audit, Analytics, Monitoring (track user activity)
 */
@Getter
public class UserLoginEvent extends DomainEvent {

  private final UUID userId;
  private final String contactValue;
  private final String ipAddress;

  public UserLoginEvent(UUID tenantId, UUID userId, String contactValue, String ipAddress) {
    super(tenantId, "USER_LOGIN");
    this.userId = userId;
    this.contactValue = contactValue;
    this.ipAddress = ipAddress;
  }

  @JsonCreator
  public UserLoginEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("contactValue") String contactValue,
      @JsonProperty("ipAddress") String ipAddress) {
    super(
        eventId, tenantId, eventType != null ? eventType : "USER_LOGIN", occurredAt, correlationId);
    this.userId = userId;
    this.contactValue = contactValue;
    this.ipAddress = ipAddress;
  }
}
