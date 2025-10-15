package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.api.dto.request.*;
import com.fabricmanagement.contact.api.dto.response.*;
import com.fabricmanagement.contact.application.mapper.ContactMapper;
import com.fabricmanagement.contact.application.mapper.ContactEventMapper;
import com.fabricmanagement.contact.domain.aggregate.Contact;
import com.fabricmanagement.contact.infrastructure.repository.ContactRepository;
import com.fabricmanagement.contact.infrastructure.messaging.NotificationService;
import com.fabricmanagement.shared.domain.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ContactService {
    
    private final ContactRepository contactRepository;
    private final DomainEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final ContactMapper contactMapper;
    private final ContactEventMapper contactEventMapper;
    
    @Transactional
    public ContactResponse createContact(CreateContactRequest request) {
        String contactType = request.getContactType();
        log.info("Creating contact for owner: {} type: {}", request.getOwnerId(), contactType);

        // ADDRESS type handled by AddressService (separate flow)
        if ("ADDRESS".equals(contactType)) {
            throw new IllegalArgumentException("ADDRESS type must be created via AddressService");
        }

        // Validate contact value (not needed for ADDRESS)
        validateContactValue(request.getContactValue(), contactType);

        // Check duplicate (except PHONE_EXTENSION - can have multiple)
        if (!"PHONE_EXTENSION".equals(contactType)) {
            if (contactRepository.existsByContactValue(request.getContactValue())) {
                throw new IllegalArgumentException("Contact value already exists: " + request.getContactValue());
            }
        }
        
        UUID ownerId = UUID.fromString(request.getOwnerId());
        
        if (request.isPrimary()) {
            contactRepository.removePrimaryStatusForOwner(ownerId);
        }
        
        Contact contact = contactMapper.fromCreateRequest(request);
        
        // PHONE_EXTENSION doesn't need verification
        if (!"PHONE_EXTENSION".equals(contactType)) {
            if (!request.isAutoVerified()) {
                String code = contact.generateVerificationCode();
                notificationService.sendVerificationCode(contact.getContactValue(), code, contact.getContactType());
            } else {
                contact.setVerified(true);
                contact.setVerifiedAt(java.time.LocalDateTime.now());
            }
        } else {
            // Extensions are auto-verified
            contact.setVerified(true);
            contact.setVerifiedAt(java.time.LocalDateTime.now());
        }
        
        contact = contactRepository.save(contact);
        
        eventPublisher.publish(contactEventMapper.toCreatedEvent(contact));
        
        log.info("Contact created successfully: {} (type: {})", contact.getId(), contactType);
        
        return contactMapper.toResponse(contact);
    }
    
    @Transactional(readOnly = true)
    public List<ContactResponse> getContactsByOwner(UUID ownerId) {
        log.debug("Getting contacts for owner: {}", ownerId);
        
        List<Contact> contacts = contactRepository.findByOwnerId(ownerId);
        
        return contacts.stream()
                .map(contactMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public ContactResponse getContact(UUID contactId) {
        log.debug("Getting contact: {}", contactId);
        
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));
        
        return contactMapper.toResponse(contact);
    }
    
    @Transactional
    public ContactResponse verifyContact(UUID contactId, String code) {
        log.info("Verifying contact: {}", contactId);
        
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));
        
        contact.verify(code);
        contact = contactRepository.save(contact);
        
        eventPublisher.publish(contactEventMapper.toUpdatedEvent(contact, "VERIFIED"));
        
        log.info("Contact verified successfully: {}", contactId);
        
        return contactMapper.toResponse(contact);
    }
    
    public void setPrimaryContact(UUID contactId) {
        makePrimary(contactId);
    }
    
    @Transactional
    public ContactResponse makePrimary(UUID contactId) {
        log.info("Making contact primary: {}", contactId);
        
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));
        
        contactRepository.removePrimaryStatusForOwner(contact.getOwnerId());
        
        contact.makePrimary();
        contact = contactRepository.save(contact);
        
        eventPublisher.publish(contactEventMapper.toUpdatedEvent(contact, "PRIMARY_CHANGED"));
        
        log.info("Contact made primary successfully: {}", contactId);
        
        return contactMapper.toResponse(contact);
    }
    
    @Transactional
    public void updateContact(UUID contactId, UpdateContactRequest request) {
        log.info("Updating contact: {}", contactId);

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));

        if (request.getContactValue() != null) {
            String contactType = request.getContactType() != null ? 
                    request.getContactType() : contact.getContactType().name();
            validateContactValue(request.getContactValue(), contactType);
        }
        
        contactMapper.updateFromRequest(contact, request);
        
        contactRepository.save(contact);
        
        log.info("Contact updated successfully: {}", contactId);
    }
    
    @Transactional
    public void deleteContact(UUID contactId) {
        log.info("Deleting contact: {}", contactId);
        
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));
        
        contact.markAsDeleted();
        contactRepository.save(contact);
        
        eventPublisher.publish(contactEventMapper.toDeletedEvent(contact));
        
        log.info("Contact deleted successfully: {}", contactId);
    }
    
    @Transactional(readOnly = true)
    public List<ContactResponse> searchContacts(UUID ownerId, String contactType) {
        log.debug("Searching contacts for owner: {} with type: {}", ownerId, contactType);
        
        List<Contact> contacts;
        if (contactType != null && !contactType.isEmpty()) {
            contacts = contactRepository.findByOwnerIdAndContactType(ownerId, contactType);
        } else {
            contacts = contactRepository.findByOwnerId(ownerId);
        }
        
        return contacts.stream()
                .filter(Contact::isNotDeleted)
                .map(contactMapper::toResponse)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public boolean checkAvailability(String contactValue) {
        return !contactRepository.existsByContactValue(contactValue);
    }
    
    /**
     * Check if email domain is already registered
     * 
     * Returns list of owner IDs that use this email domain.
     * Used during tenant onboarding to detect potential duplicates.
     * 
     * @param emailDomain Email domain (e.g., "acmetekstil.com")
     * @return List of owner IDs (USER or COMPANY) using this domain
     */
    @Transactional(readOnly = true)
    public List<UUID> checkEmailDomain(String emailDomain) {
        log.info("Checking if email domain is registered: @{}", emailDomain);
        
        if (emailDomain == null || emailDomain.isBlank()) {
            return List.of();
        }
        
        // Pattern: %@domain.com
        String domainPattern = "%@" + emailDomain.toLowerCase().trim();
        
        List<Contact> contacts = contactRepository.findByEmailDomain(domainPattern);
        
        log.info("Found {} contacts with domain @{}", contacts.size(), emailDomain);
        
        // Return unique owner IDs
        return contacts.stream()
                .map(Contact::getOwnerId)
                .distinct()
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void sendVerificationCode(UUID contactId) {
        log.info("Sending verification code for contact: {}", contactId);
        
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new RuntimeException("Contact not found: " + contactId));
        
        if (contact.isVerified()) {
            throw new IllegalStateException("Contact is already verified");
        }
        
        String code = contact.generateVerificationCode();
        contactRepository.save(contact);
        
        notificationService.sendVerificationCode(contact.getContactValue(), code, contact.getContactType());
        
        log.info("Verification code sent successfully for contact: {}", contactId);
    }
    
    @Transactional(readOnly = true)
    public List<ContactResponse> getVerifiedContacts(UUID ownerId) {
        log.debug("Getting verified contacts for owner: {}", ownerId);
        
        List<Contact> contacts = contactRepository.findVerifiedContactsByOwner(ownerId);
        
        return contacts.stream()
                .map(contactMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public ContactResponse getPrimaryContact(UUID ownerId) {
        log.debug("Getting primary contact for owner: {}", ownerId);

        Contact contact = contactRepository.findPrimaryContactByOwner(ownerId)
                .orElseThrow(() -> new RuntimeException("No primary contact found for owner: " + ownerId));

        return contactMapper.toResponse(contact);
    }

    @Transactional(readOnly = true)
    public Optional<ContactResponse> findByContactValue(String contactValue) {
        log.debug("Finding contact by value: {}", contactValue);

        return contactRepository.findByContactValue(contactValue)
                .filter(Contact::isNotDeleted)
                .map(contactMapper::toResponse);
    }
    
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
                String cleanedPhone = trimmedValue.replaceAll("[\\s\\-\\(\\)]", "");
                if (!cleanedPhone.matches("^\\+?[1-9]\\d{1,14}$")) {
                    throw new IllegalArgumentException("Invalid phone number format. Use E.164 format (e.g., +905551234567)");
                }
                break;

            case "PHONE_EXTENSION":
                // Extensions are simple numbers (e.g., 101, 1234, ext:101)
                if (!trimmedValue.matches("^(ext:)?\\d{1,10}$")) {
                    throw new IllegalArgumentException("Invalid extension format. Use numbers only (e.g., 101 or ext:101)");
                }
                break;

            case "ADDRESS":
                // ADDRESS handled by AddressService
                throw new IllegalArgumentException("ADDRESS type not supported here. Use /addresses endpoint");

            case "FAX":
            case "WEBSITE":
            case "SOCIAL_MEDIA":
                if (trimmedValue.length() > 500) {
                    throw new IllegalArgumentException("Contact value is too long (max 500 characters)");
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown contact type: " + contactType);
        }
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> listAllContacts() {
        log.debug("Listing all contacts (ADMIN operation)");
        
        List<Contact> contacts = contactRepository.findAllNonDeleted();
        
        return contacts.stream()
                .map(contactMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public java.util.Map<UUID, List<ContactResponse>> getContactsByOwnersBatch(List<UUID> ownerIds) {
        log.debug("Getting contacts for {} owners in batch", ownerIds.size());
        
        if (ownerIds == null || ownerIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        
        List<Contact> allContacts = contactRepository.findByOwnerIdIn(ownerIds);
        
        return allContacts.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Contact::getOwnerId,
                        java.util.stream.Collectors.mapping(
                                contactMapper::toResponse,
                                java.util.stream.Collectors.toList()
                        )
                ));
    }
}
