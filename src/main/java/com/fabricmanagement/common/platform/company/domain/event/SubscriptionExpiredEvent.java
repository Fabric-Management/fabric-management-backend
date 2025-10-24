package com.fabricmanagement.common.platform.company.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event fired when a subscription expires.
 *
 * <p>This is CRITICAL for Policy Engine - must immediately deny access!</p>
 * <p>Listeners: Policy (invalidate cache), Notification, Analytics, Access Control</p>
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

