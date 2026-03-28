package com.fabricmanagement.platform.subscription.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a subscription expires.
 *
 * <p>This is CRITICAL for Policy Engine - must immediately deny access!
 *
 * <p>Listeners: Policy (invalidate cache), Notification, Analytics, Access Control
 */
@Getter
public class SubscriptionExpiredEvent extends DomainEvent {

  private final UUID subscriptionId;
  private final String osCode;

  public SubscriptionExpiredEvent(UUID tenantId, UUID subscriptionId, String osCode) {
    super(tenantId, "SUBSCRIPTION_EXPIRED");
    this.subscriptionId = subscriptionId;
    this.osCode = osCode;
  }
}
