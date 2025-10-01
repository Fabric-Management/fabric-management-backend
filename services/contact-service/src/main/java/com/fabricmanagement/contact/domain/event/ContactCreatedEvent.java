package com.fabricmanagement.contact.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Contact Created Domain Event
 * 
 * Published when a new contact is created in the system
 */
@Getter
@ToString
public class ContactCreatedEvent extends DomainEvent {
    
    private final UUID contactId;
    private final String ownerId;
    private final String ownerType;
    private final String contactValue;
    private final String contactType;

    public ContactCreatedEvent(UUID contactId, String ownerId, String ownerType,
                             String contactValue, String contactType) {
        super();
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.ownerType = ownerType;
        this.contactValue = contactValue;
        this.contactType = contactType;
    }

    @Override
    public String getEventType() {
        return "ContactCreated";
    }

    @Override
    public String getAggregateId() {
        return contactId.toString();
    }
}
