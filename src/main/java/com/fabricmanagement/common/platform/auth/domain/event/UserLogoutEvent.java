package com.fabricmanagement.common.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event fired when user logs out.
 *
 * <p>Listeners: Audit, Analytics, Monitoring (track session end)</p>
 */
@Getter
public class UserLogoutEvent extends DomainEvent {

    private final UUID userId;

    public UserLogoutEvent(UUID tenantId, UUID userId) {
        super(tenantId, "USER_LOGOUT");
        this.userId = userId;
    }
}

