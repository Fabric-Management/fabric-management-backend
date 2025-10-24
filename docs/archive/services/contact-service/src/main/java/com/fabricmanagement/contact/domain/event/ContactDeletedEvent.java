package com.fabricmanagement.contact.domain.event;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Contact Deleted Domain Event
 * 
 * Published when a contact is soft deleted from the system
 */
@Getter
@ToString
public class ContactDeletedEvent extends DomainEvent {
    
    private final UUID contactId;
    private final String ownerId;
    private final String ownerType;

    public ContactDeletedEvent(UUID contactId, String ownerId, String ownerType) {
        super();
        this.contactId = contactId;
        this.ownerId = ownerId;
        this.ownerType = ownerType;
    }

    @Override
    public String getEventType() {
        return "ContactDeleted";
    }

    @Override
    public String getAggregateId() {
        return contactId.toString();
    }
}
