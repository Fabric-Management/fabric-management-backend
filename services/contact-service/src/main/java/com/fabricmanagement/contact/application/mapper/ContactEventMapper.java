package com.fabricmanagement.contact.application.mapper;

import com.fabricmanagement.contact.domain.aggregate.Contact;
import com.fabricmanagement.contact.domain.event.ContactCreatedEvent;
import com.fabricmanagement.contact.domain.event.ContactUpdatedEvent;
import com.fabricmanagement.contact.domain.event.ContactDeletedEvent;
import org.springframework.stereotype.Component;

@Component
public class ContactEventMapper {

    public ContactCreatedEvent toCreatedEvent(Contact contact) {
        return new ContactCreatedEvent(
                contact.getId(),
                contact.getOwnerId().toString(),
                contact.getOwnerType().name(),
                contact.getContactValue(),
                contact.getContactType().name()
        );
    }

    public ContactUpdatedEvent toUpdatedEvent(Contact contact, String updateType) {
        return new ContactUpdatedEvent(
                contact.getId(),
                contact.getOwnerId().toString(),
                contact.getOwnerType().name(),
                updateType
        );
    }

    public ContactDeletedEvent toDeletedEvent(Contact contact) {
        return new ContactDeletedEvent(
                contact.getId(),
                contact.getOwnerId().toString(),
                contact.getOwnerType().name()
        );
    }
}

