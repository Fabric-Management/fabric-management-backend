package com.fabricmanagement.common.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event fired when a new user is created.
 *
 * <p>Listeners: Audit, Analytics, Notification, Policy (cache warm-up)</p>
 */
@Getter
public class UserCreatedEvent extends DomainEvent {

    private final UUID userId;
    private final String displayName;
    private final String contactValue;
    private final UUID companyId;

    public UserCreatedEvent(UUID tenantId, UUID userId, String displayName, 
                           String contactValue, UUID companyId) {
        super(tenantId, "USER_CREATED");
        this.userId = userId;
        this.displayName = displayName;
        this.contactValue = contactValue;
        this.companyId = companyId;
    }
}

