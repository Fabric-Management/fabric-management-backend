package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.domain.model.ContactEmail;
import com.fabricmanagement.contact.domain.model.ContactPhone;
import com.fabricmanagement.contact.domain.model.ContactAddress;
import com.fabricmanagement.contact.domain.model.UserContactInfo;
import com.fabricmanagement.contact.domain.model.UserContactInfo.EmailType;
import com.fabricmanagement.contact.domain.model.UserContactInfo.PhoneType;
import com.fabricmanagement.contact.domain.model.UserContactInfo.AddressType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing user contact information.
 * Central service for all user contact operations (emails, phones, addresses).
 *
 * This service ensures:
 * - Single source of truth for user contact information
 * - Business rules enforcement (primary contacts, verification)
 * - Integration with Identity and User services
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserContactInfoService {

    // Repository would be injected here
    // private final UserContactInfoRepository repository;

    /**
     * Gets complete contact information for a user.
     */
    @Transactional(readOnly = true)
    public UserContactInfo getUserContactInfo(UUID userId) {
        log.info("Getting contact information for user: {}", userId);
        // Implementation would fetch from repository
        return null;
    }

    /**
     * Creates initial contact information for a new user.
     */
    public UserContactInfo createUserContactInfo(UUID userId, UUID identityId) {
        log.info("Creating contact information for user: {} with identity: {}", userId, identityId);

        UserContactInfo contactInfo = UserContactInfo.builder()
            .userId(userId)
            .identityId(identityId)
            .build();

        // Save to repository
        // return repository.save(contactInfo);
        return contactInfo;
    }

    /**
     * Adds an email to user's contact information.
     */
    public void addEmail(UUID userId, String email, EmailType type, boolean isPrimary) {
        log.info("Adding email for user: {} - type: {}, primary: {}", userId, type, isPrimary);

        UserContactInfo contactInfo = getUserContactInfo(userId);
        if (contactInfo == null) {
            throw new IllegalArgumentException("User contact info not found for: " + userId);
        }

        contactInfo.addEmail(email, type, isPrimary);
        // Save to repository
        // repository.save(contactInfo);
    }

    /**
     * Adds a phone to user's contact information.
     */
    public void addPhone(UUID userId, String phoneNumber, PhoneType type, boolean isPrimary) {
        log.info("Adding phone for user: {} - type: {}, primary: {}", userId, type, isPrimary);

        UserContactInfo contactInfo = getUserContactInfo(userId);
        if (contactInfo == null) {
            throw new IllegalArgumentException("User contact info not found for: " + userId);
        }

        contactInfo.addPhone(phoneNumber, type, isPrimary);
        // Save to repository
        // repository.save(contactInfo);
    }

    /**
     * Adds an address to user's contact information.
     */
    public void addAddress(UUID userId, String street, String city, String state,
                          String country, String postalCode, AddressType type) {
        log.info("Adding address for user: {} - type: {}", userId, type);

        UserContactInfo contactInfo = getUserContactInfo(userId);
        if (contactInfo == null) {
            throw new IllegalArgumentException("User contact info not found for: " + userId);
        }

        contactInfo.addAddress(street, city, state, country, postalCode, type);
        // Save to repository
        // repository.save(contactInfo);
    }

    /**
     * Gets primary email for a user.
     */
    @Transactional(readOnly = true)
    public ContactEmail getPrimaryEmail(UUID userId) {
        log.debug("Getting primary email for user: {}", userId);

        UserContactInfo contactInfo = getUserContactInfo(userId);
        return contactInfo != null ? contactInfo.getPrimaryEmail() : null;
    }

    /**
     * Gets primary phone for a user.
     */
    @Transactional(readOnly = true)
    public ContactPhone getPrimaryPhone(UUID userId) {
        log.debug("Getting primary phone for user: {}", userId);

        UserContactInfo contactInfo = getUserContactInfo(userId);
        return contactInfo != null ? contactInfo.getPrimaryPhone() : null;
    }

    /**
     * Verifies an email for a user.
     */
    public void verifyEmail(UUID userId, UUID emailId) {
        log.info("Verifying email {} for user: {}", emailId, userId);

        UserContactInfo contactInfo = getUserContactInfo(userId);
        if (contactInfo == null) {
            throw new IllegalArgumentException("User contact info not found for: " + userId);
        }

        contactInfo.verifyEmail(emailId);
        // Save to repository
        // repository.save(contactInfo);
    }

    /**
     * Verifies a phone for a user.
     */
    public void verifyPhone(UUID userId, UUID phoneId) {
        log.info("Verifying phone {} for user: {}", phoneId, userId);

        UserContactInfo contactInfo = getUserContactInfo(userId);
        if (contactInfo == null) {
            throw new IllegalArgumentException("User contact info not found for: " + userId);
        }

        contactInfo.verifyPhone(phoneId);
        // Save to repository
        // repository.save(contactInfo);
    }

    /**
     * Updates user contact info when user is deleted (soft delete).
     */
    public void handleUserDeleted(UUID userId) {
        log.info("Handling user deletion for contact info: {}", userId);

        UserContactInfo contactInfo = getUserContactInfo(userId);
        if (contactInfo != null) {
            contactInfo.setDeleted(true);
            // Save to repository
            // repository.save(contactInfo);
        }
    }
}