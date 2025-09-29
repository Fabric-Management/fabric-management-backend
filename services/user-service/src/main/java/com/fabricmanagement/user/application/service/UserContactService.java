package com.fabricmanagement.user.application.service;

import com.fabricmanagement.user.application.dto.contact.request.AddContactRequest;
import com.fabricmanagement.user.application.dto.contact.response.UserContactResponse;
import com.fabricmanagement.user.domain.model.UserContact;
import com.fabricmanagement.user.domain.repository.UserContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for user contact management.
 * Handles user contact operations (email, phone).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserContactService {

    private final UserContactRepository userContactRepository;

    /**
     * Gets all contacts for a user.
     */
    @Transactional(readOnly = true)
    public List<UserContactResponse> getUserContacts(UUID userId) {
        log.debug("Getting contacts for user: {}", userId);
        
        return userContactRepository.findByUserId(userId)
            .stream()
            .map(UserContactResponse::from)
            .toList();
    }

    /**
     * Adds a new contact to user.
     */
    public UserContactResponse addUserContact(UUID userId, AddContactRequest request) {
        log.info("Adding contact for user: {} - {}", userId, request.contactType());
        
        // Check if contact already exists
        if (userContactRepository.existsByValue(request.contactValue())) {
            throw new IllegalArgumentException("Contact already exists: " + request.contactValue());
        }
        
        // Validate contact type and value
        UserContact.ContactType contactType = UserContact.ContactType.valueOf(request.contactType());
        
        UserContact contact = UserContact.create(userId, contactType, request.contactValue());
        
        // If this is the first contact, make it primary
        List<UserContact> existingContacts = userContactRepository.findByUserId(userId);
        if (existingContacts.isEmpty()) {
            contact.markAsPrimary();
        }
        
        UserContact savedContact = userContactRepository.save(contact);
        
        log.info("Contact added successfully for user: {} - {}", userId, request.contactValue());
        return UserContactResponse.from(savedContact);
    }

    /**
     * Removes a contact from user.
     */
    public void removeUserContact(UUID userId, UUID contactId) {
        log.info("Removing contact for user: {} - {}", userId, contactId);
        
        UserContact contact = userContactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));
        
        // Ensure contact belongs to user
        if (!contact.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Contact does not belong to user");
        }
        
        // Cannot remove primary contact if there are other contacts
        if (contact.isPrimary()) {
            List<UserContact> otherContacts = userContactRepository.findByUserId(userId)
                .stream()
                .filter(c -> !c.getId().equals(contactId))
                .toList();
            
            if (!otherContacts.isEmpty()) {
                throw new IllegalArgumentException("Cannot remove primary contact. Set a different primary first.");
            }
        }
        
        userContactRepository.deleteById(contactId);
        
        log.info("Contact removed successfully for user: {} - {}", userId, contactId);
    }

    /**
     * Sets primary contact.
     */
    public void setPrimaryContact(UUID userId, UUID contactId) {
        log.info("Setting primary contact for user: {} - {}", userId, contactId);
        
        UserContact contact = userContactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));
        
        // Ensure contact belongs to user
        if (!contact.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Contact does not belong to user");
        }
        
        // Ensure contact is verified
        if (!contact.isVerified()) {
            throw new IllegalArgumentException("Cannot set unverified contact as primary");
        }
        
        // Remove primary flag from current primary
        userContactRepository.findPrimaryContactByUserId(userId)
            .ifPresent(UserContact::unmarkAsPrimary);
        
        // Set new primary
        contact.markAsPrimary();
        userContactRepository.save(contact);
        
        log.info("Primary contact set successfully for user: {} - {}", userId, contactId);
    }

    /**
     * Verifies a contact.
     */
    public void verifyContact(UUID userId, UUID contactId) {
        log.info("Verifying contact for user: {} - {}", userId, contactId);
        
        UserContact contact = userContactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));
        
        // Ensure contact belongs to user
        if (!contact.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Contact does not belong to user");
        }
        
        contact.verify();
        userContactRepository.save(contact);
        
        log.info("Contact verified successfully for user: {} - {}", userId, contactId);
    }

    /**
     * Gets primary contact for user.
     */
    @Transactional(readOnly = true)
    public UserContactResponse getPrimaryContact(UUID userId) {
        log.debug("Getting primary contact for user: {}", userId);
        
        return userContactRepository.findPrimaryContactByUserId(userId)
            .map(UserContactResponse::from)
            .orElse(null);
    }
}
