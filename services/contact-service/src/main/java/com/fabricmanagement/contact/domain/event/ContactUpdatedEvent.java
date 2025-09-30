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
    private final UUID tenantId;
    private final UUID userId;
    private final UUID companyId;
    private final String contactType;
    private final String displayName;

    public ContactUpdatedEvent(UUID contactId, UUID tenantId, UUID userId, UUID companyId,
                             String contactType, String displayName) {
        super();
        this.contactId = contactId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.companyId = companyId;
        this.contactType = contactType;
        this.displayName = displayName;
    }

    @Override
    public String getEventType() {
        return "ContactUpdated";
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
