package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user contact is added.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserContactAddedEvent extends DomainEvent {
    private UUID userId;
    private UUID contactId;
    private String contactType;
    private String contactValue;

    public UserContactAddedEvent(UUID userId, UUID contactId, String contactType, String contactValue) {
        super("UserContactAdded");
        this.userId = userId;
        this.contactId = contactId;
        this.contactType = contactType;
        this.contactValue = contactValue;
    }
}