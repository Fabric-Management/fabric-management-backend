package com.fabricmanagement.platform.subscription.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a subscription is activated.
 *
 * <p>This is CRITICAL for Policy Engine - invalidates policy cache!
 *
 * <p>Listeners: Policy (invalidate cache), Notification, Analytics
 */
@Getter
public class SubscriptionActivatedEvent extends DomainEvent {

  private final UUID subscriptionId;
  private final String osCode;
  private final String osName;

  public SubscriptionActivatedEvent(
      UUID tenantId, UUID subscriptionId, String osCode, String osName) {
    super(tenantId, "SUBSCRIPTION_ACTIVATED");
    this.subscriptionId = subscriptionId;
    this.osCode = osCode;
    this.osName = osName;
  }

  @JsonCreator
  public SubscriptionActivatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("subscriptionId") UUID subscriptionId,
      @JsonProperty("osCode") String osCode,
      @JsonProperty("osName") String osName) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SUBSCRIPTION_ACTIVATED",
        occurredAt,
        correlationId);
    this.subscriptionId = subscriptionId;
    this.osCode = osCode;
    this.osName = osName;
  }
}
