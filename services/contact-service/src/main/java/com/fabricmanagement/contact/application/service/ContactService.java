package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.application.dto.*;
import com.fabricmanagement.contact.domain.aggregate.Contact;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import com.fabricmanagement.contact.infrastructure.repository.ContactRepository;
import com.fabricmanagement.shared.domain.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Contact Application Service
 * 
 * Handles business logic for contact management
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ContactService {
    
    private final ContactRepository contactRepository;
    private final DomainEventPublisher eventPublisher;
    private final NotificationService notificationService;
    
    /**
     * Creates a new contact
     */
    public ContactResponse createContact(CreateContactRequest request) {
        log.info("Creating contact for owner: {} with value: {}", request.getOwnerId(), request.getContactValue());

        // Validate contact value based on contact type
        validateContactValue(request.getContactValue(), request.getContactType());

        // Check if contact already exists
        if (contactRepository.existsByContactValue(request.getContactValue())) {
            throw new IllegalArgumentException("Contact value already exists: " + request.getContactValue());
        }
        
        // If this should be primary, remove primary status from other contacts
        if (request.isPrimary()) {
            contactRepository.removePrimaryStatusForOwner(request.getOwnerId());
        }
        
        // Create new contact
        Contact contact = Contact.create(
            request.getOwnerId(),
            Contact.OwnerType.valueOf(request.getOwnerType()),
            request.getContactValue(),
            ContactType.valueOf(request.getContactType()),
            request.isPrimary()
        );
        
        // Generate verification code if not auto-verified
        if (!request.isAutoVerified()) {
            String code = contact.generateVerificationCode();
            // Send verification code
            notificationService.sendVerificationCode(contact.getContactValue(), code, contact.getContactType());
        } else {
            // Auto-verify for internal creation
            contact.setVerified(true);
            contact.setVerifiedAt(java.time.LocalDateTime.now());
        }
        
        // Save contact
        contact = contactRepository.save(contact);
        
        // Publish domain events
        contact.getAndClearDomainEvents().forEach(eventPublisher::publish);
        
        log.info("Contact created successfully with ID: {}", contact.getId());
        
        return toContactResponse(contact);
    }
    
    /**
     * Gets contacts by owner ID
     */
    @Transactional(readOnly = true)
    public List<ContactResponse> getContactsByOwner(String ownerId) {
        log.debug("Getting contacts for owner: {}", ownerId);
        
        List<Contact> contacts = contactRepository.findByOwnerId(ownerId);
        
        return contacts.stream()
            .map(this::toContactResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets a specific contact
     */
    @Transactional(readOnly = true)
    public ContactResponse getContact(UUID contactId) {
        log.debug("Getting contact: {}", contactId);
        
        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));
        
        return toContactResponse(contact);
    }
    
    /**
     * Verifies a contact
     */
    public ContactResponse verifyContact(UUID contactId, String code) {
        log.info("Verifying contact: {}", contactId);
        
        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));
        
        contact.verify(code);
        contact = contactRepository.save(contact);
        
        // Publish domain events
        contact.getAndClearDomainEvents().forEach(eventPublisher::publish);
        
        log.info("Contact verified successfully: {}", contactId);
        
        return toContactResponse(contact);
    }
    
    /**
     * Sets a contact as primary (alias for makePrimary)
     */
    public void setPrimaryContact(UUID contactId) {
        makePrimary(contactId);
    }
    
    /**
     * Makes a contact primary
     */
    public ContactResponse makePrimary(UUID contactId) {
        log.info("Making contact primary: {}", contactId);
        
        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));
        
        // Remove primary status from other contacts
        contactRepository.removePrimaryStatusForOwner(contact.getOwnerId());
        
        // Make this contact primary
        contact.makePrimary();
        contact = contactRepository.save(contact);
        
        // Publish domain events
        contact.getAndClearDomainEvents().forEach(eventPublisher::publish);
        
        log.info("Contact made primary successfully: {}", contactId);
        
        return toContactResponse(contact);
    }
    
    /**
     * Updates a contact
     */
    public void updateContact(UUID contactId, com.fabricmanagement.contact.application.dto.UpdateContactRequest request) {
        log.info("Updating contact: {}", contactId);

        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));

        // Validate if contactValue is being updated
        if (request.getContactValue() != null) {
            String contactType = request.getContactType() != null ? request.getContactType() : contact.getContactType().name();
            validateContactValue(request.getContactValue(), contactType);
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
        
        contactRepository.save(contact);
        
        log.info("Contact updated successfully: {}", contactId);
    }
    
    /**
     * Deletes a contact
     */
    public void deleteContact(UUID contactId) {
        log.info("Deleting contact: {}", contactId);
        
        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));
        
        contact.markAsDeleted();
        contactRepository.save(contact);
        
        // Publish domain events
        contact.getAndClearDomainEvents().forEach(eventPublisher::publish);
        
        log.info("Contact deleted successfully: {}", contactId);
    }
    
    /**
     * Searches contacts by owner and type
     */
    @Transactional(readOnly = true)
    public List<ContactResponse> searchContacts(String ownerId, String contactType) {
        log.debug("Searching contacts for owner: {} with type: {}", ownerId, contactType);
        
        List<Contact> contacts;
        if (contactType != null && !contactType.isEmpty()) {
            contacts = contactRepository.findByOwnerIdAndContactType(ownerId, contactType);
        } else {
            contacts = contactRepository.findByOwnerId(ownerId);
        }
        
        return contacts.stream()
                .filter(Contact::isNotDeleted)
                .map(this::toContactResponse)
                .toList();
    }
    
    /**
     * Checks if a contact value is available
     */
    @Transactional(readOnly = true)
    public boolean checkAvailability(String contactValue) {
        return !contactRepository.existsByContactValue(contactValue);
    }
    
    /**
     * Sends verification code to a contact
     */
    public void sendVerificationCode(UUID contactId) {
        log.info("Sending verification code for contact: {}", contactId);
        
        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));
        
        if (contact.isVerified()) {
            throw new IllegalStateException("Contact is already verified");
        }
        
        String code = contact.generateVerificationCode();
        contactRepository.save(contact);
        
        // Send verification code
        notificationService.sendVerificationCode(contact.getContactValue(), code, contact.getContactType());
        
        log.info("Verification code sent successfully for contact: {}", contactId);
    }
    
    /**
     * Gets verified contacts for an owner
     */
    @Transactional(readOnly = true)
    public List<ContactResponse> getVerifiedContacts(String ownerId) {
        log.debug("Getting verified contacts for owner: {}", ownerId);
        
        List<Contact> contacts = contactRepository.findVerifiedContactsByOwner(ownerId);
        
        return contacts.stream()
            .map(this::toContactResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets primary contact for an owner
     */
    @Transactional(readOnly = true)
    public ContactResponse getPrimaryContact(String ownerId) {
        log.debug("Getting primary contact for owner: {}", ownerId);

        Contact contact = contactRepository.findPrimaryContactByOwner(ownerId)
            .orElseThrow(() -> new RuntimeException("No primary contact found for owner: " + ownerId));

        return toContactResponse(contact);
    }

    /**
     * Finds contact by contact value (email or phone)
     * Returns Optional since contact_value is UNIQUE in database
     */
    @Transactional(readOnly = true)
    public Optional<ContactResponse> findByContactValue(String contactValue) {
        log.debug("Finding contact by value: {}", contactValue);

        return contactRepository.findByContactValue(contactValue)
                .filter(Contact::isNotDeleted)
                .map(this::toContactResponse);
    }
    
    /**
     * Validates contact value based on contact type
     */
    private void validateContactValue(String contactValue, String contactType) {
        if (contactValue == null || contactValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Contact value cannot be empty");
        }

        String trimmedValue = contactValue.trim();

        switch (contactType.toUpperCase()) {
            case "EMAIL":
                if (!trimmedValue.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    throw new IllegalArgumentException("Invalid email format: " + contactValue);
                }
                break;

            case "PHONE":
                // Remove common formatting characters
                String cleanedPhone = trimmedValue.replaceAll("[\\s\\-\\(\\)]", "");
                if (!cleanedPhone.matches("^\\+?[1-9]\\d{1,14}$")) {
                    throw new IllegalArgumentException("Invalid phone number format. Use E.164 format (e.g., +905551234567)");
                }
                break;

            case "ADDRESS":
            case "FAX":
            case "WEBSITE":
            case "SOCIAL_MEDIA":
                // Basic validation for other types
                if (trimmedValue.length() > 500) {
                    throw new IllegalArgumentException("Contact value is too long (max 500 characters)");
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown contact type: " + contactType);
        }
    }

    /**
     * Converts Contact entity to ContactResponse DTO
     */
    private ContactResponse toContactResponse(Contact contact) {
        return ContactResponse.builder()
            .id(contact.getId())
            .ownerId(contact.getOwnerId())
            .ownerType(contact.getOwnerType().name())
            .contactValue(contact.getContactValue())
            .contactType(contact.getContactType().name())
            .isVerified(contact.isVerified())
            .isPrimary(contact.isPrimary())
            .verifiedAt(contact.getVerifiedAt())
            .createdAt(contact.getCreatedAt())
            .updatedAt(contact.getUpdatedAt())
            .build();
    }
}
