package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user contact is removed.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserContactRemovedEvent extends DomainEvent {
    private UUID userId;
    private UUID contactId;

    public UserContactRemovedEvent(UUID userId, UUID contactId) {
        super("UserContactRemoved");
        this.userId = userId;
        this.contactId = contactId;
    }
}