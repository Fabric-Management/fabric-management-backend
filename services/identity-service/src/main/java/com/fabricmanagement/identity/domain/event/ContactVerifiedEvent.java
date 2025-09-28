package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when a user contact is verified.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContactVerifiedEvent extends DomainEvent {
    private UUID userId;
    private UUID contactId;
    private String contactValue;

    public ContactVerifiedEvent(UUID userId, UUID contactId, String contactValue) {
        super("ContactVerified");
        this.userId = userId;
        this.contactId = contactId;
        this.contactValue = contactValue;
    }
}