package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user is activated.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserActivatedEvent extends DomainEvent {
    private UUID userId;

    public UserActivatedEvent(UUID userId) {
        super("UserActivated");
        this.userId = userId;
    }
}