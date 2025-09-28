package com.fabricmanagement.identity.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Event fired when contact verification is initiated.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContactVerificationInitiatedEvent extends DomainEvent {
    private UUID userId;
    private UUID contactId;
    private String contactValue;

    public ContactVerificationInitiatedEvent(UUID userId, UUID contactId, String contactValue) {
        super("ContactVerificationInitiated");
        this.userId = userId;
        this.contactId = contactId;
        this.contactValue = contactValue;
    }
}