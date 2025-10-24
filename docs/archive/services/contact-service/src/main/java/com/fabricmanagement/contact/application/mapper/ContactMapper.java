package com.fabricmanagement.contact.application.mapper;

import com.fabricmanagement.contact.api.dto.request.CreateContactRequest;
import com.fabricmanagement.contact.api.dto.request.UpdateContactRequest;
import com.fabricmanagement.contact.api.dto.response.ContactResponse;
import com.fabricmanagement.contact.domain.aggregate.Contact;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ContactMapper {

    public ContactResponse toResponse(Contact contact) {
        return ContactResponse.builder()
                .id(contact.getId())
                .ownerId(contact.getOwnerId().toString())
                .ownerType(contact.getOwnerType().name())
                .contactValue(contact.getContactValue())
                .contactType(contact.getContactType().name())
                .parentContactId(contact.getParentContactId() != null 
                    ? contact.getParentContactId().toString() 
                    : null)
                .isVerified(contact.isVerified())
                .isPrimary(contact.isPrimary())
                .verificationCode(contact.getVerificationCode()) // ✅ For UserCreatedEvent
                .verifiedAt(contact.getVerifiedAt())
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .build();
    }

    public Contact fromCreateRequest(CreateContactRequest request) {
        UUID ownerId = UUID.fromString(request.getOwnerId());
        UUID parentContactId = request.getParentContactId() != null 
            ? UUID.fromString(request.getParentContactId()) 
            : null;
        
        Contact contact = Contact.create(
                ownerId,
                Contact.OwnerType.valueOf(request.getOwnerType()),
                request.getContactValue(),
                ContactType.valueOf(request.getContactType()),
                request.isPrimary()
        );
        
        contact.setParentContactId(parentContactId);
        return contact;
    }

    public void updateFromRequest(Contact contact, UpdateContactRequest request) {
        if (request.getContactValue() != null) {
            contact.setContactValue(request.getContactValue());
        }
        if (request.getContactType() != null) {
            contact.setContactType(ContactType.valueOf(request.getContactType()));
        }
        if (request.getIsPrimary() != null) {
            contact.setPrimary(request.getIsPrimary());
        }
        if (request.getIsVerified() != null) {
            contact.setVerified(request.getIsVerified());
        }
    }
}

