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
    private final UUID tenantId;
    private final UUID userId;
    private final UUID companyId;
    private final String contactType;
    private final String email;

    public ContactDeletedEvent(UUID contactId, UUID tenantId, UUID userId, UUID companyId,
                              String contactType, String email) {
        super();
        this.contactId = contactId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.companyId = companyId;
        this.contactType = contactType;
        this.email = email;
    }

    @Override
    public String getEventType() {
        return "ContactDeleted";
    }

    @Override
    public String getAggregateId() {
        return contactId.toString();
    }

    @Override
    public String getTenantId() {
        return tenantId.toString();
    }
}
