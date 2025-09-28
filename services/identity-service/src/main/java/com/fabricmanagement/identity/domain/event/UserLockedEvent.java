package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user is locked.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserLockedEvent extends DomainEvent {
    private UUID userId;

    public UserLockedEvent(UUID userId) {
        super("UserLocked");
        this.userId = userId;
    }
}