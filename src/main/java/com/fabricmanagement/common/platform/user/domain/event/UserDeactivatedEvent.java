package com.fabricmanagement.common.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event fired when a user is deactivated.
 *
 * <p>CRITICAL: Must invalidate policy cache and revoke active sessions!</p>
 * <p>Listeners: Policy (invalidate cache), Auth (revoke tokens), Audit</p>
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
}

