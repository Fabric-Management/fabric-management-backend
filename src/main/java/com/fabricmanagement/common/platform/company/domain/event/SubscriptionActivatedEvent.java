package com.fabricmanagement.common.platform.company.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event fired when a subscription is activated.
 *
 * <p>This is CRITICAL for Policy Engine - invalidates policy cache!</p>
 * <p>Listeners: Policy (invalidate cache), Notification, Analytics</p>
 */
@Getter
public class SubscriptionActivatedEvent extends DomainEvent {

    private final UUID subscriptionId;
    private final String osCode;
    private final String osName;

    public SubscriptionActivatedEvent(UUID tenantId, UUID subscriptionId, String osCode, String osName) {
        super(tenantId, "SUBSCRIPTION_ACTIVATED");
        this.subscriptionId = subscriptionId;
        this.osCode = osCode;
        this.osName = osName;
    }
}

