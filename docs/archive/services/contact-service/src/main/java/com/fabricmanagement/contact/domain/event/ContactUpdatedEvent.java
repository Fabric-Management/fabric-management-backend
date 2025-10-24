package com.fabricmanagement.contact.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Contact Updated Domain Event
 * 
 * Published when a contact's information is updated
 */
@Getter
@ToString
public class ContactUpdatedEvent extends DomainEvent {
    
    private final UUID contactId;
    private final String ownerId;
    private final String ownerType;
    private final String updateType;

    public ContactUpdatedEvent(UUID contactId, String ownerId, String ownerType, String updateType) {
        super();
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.ownerType = ownerType;
        this.updateType = updateType;
    }

    @Override
    public String getEventType() {
        return "ContactUpdated";
    }

    @Override
    public String getAggregateId() {
        return contactId.toString();
    }
}
