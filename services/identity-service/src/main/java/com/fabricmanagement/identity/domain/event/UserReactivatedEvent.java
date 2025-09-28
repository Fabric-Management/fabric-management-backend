package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user is reactivated.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserReactivatedEvent extends DomainEvent {
    private UUID userId;

    public UserReactivatedEvent(UUID userId) {
        super("UserReactivated");
        this.userId = userId;
    }
}