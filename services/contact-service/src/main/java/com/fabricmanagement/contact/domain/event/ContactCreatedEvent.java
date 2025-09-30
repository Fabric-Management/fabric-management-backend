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
    private final UUID tenantId;
    private final UUID userId;
    private final UUID companyId;
    private final String contactType;
    private final String email;
    private final String displayName;

    public ContactCreatedEvent(UUID contactId, UUID tenantId, UUID userId, UUID companyId,
                             String contactType, String email, String displayName) {
        super();
        this.contactId = contactId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.companyId = companyId;
        this.contactType = contactType;
        this.email = email;
        this.displayName = displayName;
    }

    @Override
    public String getEventType() {
        return "ContactCreated";
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
