package com.fabricmanagement.common.platform.auth.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event fired when user successfully logs in.
 *
 * <p>Listeners: Audit, Analytics, Monitoring (track user activity)</p>
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
}

