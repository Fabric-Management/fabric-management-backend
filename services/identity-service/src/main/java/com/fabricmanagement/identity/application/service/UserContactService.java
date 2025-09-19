package com.fabricmanagement.identity.application.service;

import com.fabricmanagement.identity.domain.model.UserContact;
import com.fabricmanagement.identity.domain.repository.UserContactRepository;
import com.fabricmanagement.identity.domain.valueobject.ContactStatus;
import com.fabricmanagement.identity.domain.valueobject.ContactType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user contacts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserContactService {
    
    private final UserContactRepository userContactRepository;
    
    /**
     * Creates a new user contact.
     */
    public UserContact createUserContact(UserContact userContact) {
        log.debug("Creating user contact for user: {}", userContact.getUserId());
        
        userContact.setContactType(ContactType.USER);
        userContact.setStatus(ContactStatus.ACTIVE);
        userContact.setIsActive(true);
        
        return userContactRepository.save(userContact);
    }
    
    /**
     * Updates an existing user contact.
     */
    public UserContact updateUserContact(UUID contactId, UserContact updatedContact) {
        log.debug("Updating user contact: {}", contactId);
        
        UserContact existingContact = userContactRepository.findById(contactId)
            .orElseThrow(() -> new RuntimeException("User contact not found: " + contactId));
        
        existingContact.setJobTitle(updatedContact.getJobTitle());
        existingContact.setDepartment(updatedContact.getDepartment());
        existingContact.setTimeZone(updatedContact.getTimeZone());
        existingContact.setLanguagePreference(updatedContact.getLanguagePreference());
        existingContact.setPreferredContactMethod(updatedContact.getPreferredContactMethod());
        existingContact.setPrimaryEmail(updatedContact.getPrimaryEmail());
        existingContact.setPrimaryPhone(updatedContact.getPrimaryPhone());
        existingContact.setPrimaryAddress(updatedContact.getPrimaryAddress());
        existingContact.setStatus(updatedContact.getStatus());
        existingContact.setIsActive(updatedContact.getIsActive());
        
        return userContactRepository.save(existingContact);
    }
    
    /**
     * Gets user contact by ID.
     */
    @Transactional(readOnly = true)
    public Optional<UserContact> getUserContactById(UUID contactId) {
        log.debug("Fetching user contact by ID: {}", contactId);
        return userContactRepository.findById(contactId);
    }
    
    /**
     * Gets user contacts by user ID.
     */
    @Transactional(readOnly = true)
    public List<UserContact> getUserContactsByUserId(UUID userId) {
        log.debug("Fetching user contacts for user: {}", userId);
        return userContactRepository.findByUserId(userId);
    }
    
    /**
     * Gets user contacts by tenant ID.
     */
    @Transactional(readOnly = true)
    public List<UserContact> getUserContactsByTenantId(UUID tenantId) {
        log.debug("Fetching user contacts for tenant: {}", tenantId);
        return userContactRepository.findByTenantId(tenantId);
    }
    
    /**
     * Deletes a user contact.
     */
    public void deleteUserContact(UUID contactId) {
        log.debug("Deleting user contact: {}", contactId);
        
        UserContact contact = userContactRepository.findById(contactId)
            .orElseThrow(() -> new RuntimeException("User contact not found: " + contactId));
        
        contact.setIsDeleted(true);
        contact.setStatus(ContactStatus.INACTIVE);
        contact.setIsActive(false);
        
        userContactRepository.save(contact);
    }
    
    /**
     * Activates a user contact.
     */
    public UserContact activateUserContact(UUID contactId) {
        log.debug("Activating user contact: {}", contactId);
        
        UserContact contact = userContactRepository.findById(contactId)
            .orElseThrow(() -> new RuntimeException("User contact not found: " + contactId));
        
        contact.setStatus(ContactStatus.ACTIVE);
        contact.setIsActive(true);
        
        return userContactRepository.save(contact);
    }
    
    /**
     * Deactivates a user contact.
     */
    public UserContact deactivateUserContact(UUID contactId) {
        log.debug("Deactivating user contact: {}", contactId);
        
        UserContact contact = userContactRepository.findById(contactId)
            .orElseThrow(() -> new RuntimeException("User contact not found: " + contactId));
        
        contact.setStatus(ContactStatus.INACTIVE);
        contact.setIsActive(false);
        
        return userContactRepository.save(contact);
    }
}
