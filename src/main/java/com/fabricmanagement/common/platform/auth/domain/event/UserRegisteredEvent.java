package com.fabricmanagement.common.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event fired when user completes registration.
 *
 * <p>Listeners: Audit, Analytics, Notification (welcome email)</p>
 */
@Getter
public class UserRegisteredEvent extends DomainEvent {

    private final UUID userId;
    private final String contactValue;

    public UserRegisteredEvent(UUID tenantId, UUID userId, String contactValue) {
        super(tenantId, "USER_REGISTERED");
        this.userId = userId;
        this.contactValue = contactValue;
    }
}

