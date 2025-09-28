package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user profile is updated.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserProfileUpdatedEvent extends DomainEvent {
    private UUID userId;

    public UserProfileUpdatedEvent(UUID userId) {
        super("UserProfileUpdated");
        this.userId = userId;
    }
}