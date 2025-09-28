package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user is suspended.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserSuspendedEvent extends DomainEvent {
    private UUID userId;
    private String reason;

    public UserSuspendedEvent(UUID userId, String reason) {
        super("UserSuspended");
        this.userId = userId;
        this.reason = reason;
    }
}