package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.UserContact;
import com.fabricmanagement.common.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.common.platform.communication.infra.repository.UserContactRepository;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User Contact Service - Business logic for user-contact assignments.
 *
 * <p>Handles Many-to-Many relationship between User and Contact.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Assign contacts to users</li>
 *   <li>Remove user-contact assignments</li>
 *   <li>Manage default contact for notifications</li>
 *   <li>Manage authentication contact</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserContactService {

    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final UserContactRepository userContactRepository;

    @Transactional(readOnly = true)
    public List<UserContact> getUserContacts(UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding user contacts: tenantId={}, userId={}", tenantId, userId);

        return userContactRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<UserContact> getDefaultContact(UUID userId) {
        log.trace("Finding default contact: userId={}", userId);
        return userContactRepository.findDefaultByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<UserContact> getAuthenticationContact(UUID userId) {
        log.trace("Finding authentication contact: userId={}", userId);
        return userContactRepository.findAuthenticationContactByUserId(userId);
    }

    @Transactional
    public UserContact assignContact(UUID userId, UUID contactId, Boolean isDefault, Boolean isForAuthentication) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Assigning contact to user: tenantId={}, userId={}, contactId={}, isDefault={}, isForAuth={}",
            tenantId, userId, contactId, isDefault, isForAuthentication);

        // Validate user exists
        userRepository.findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        if (!contact.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Contact does not belong to current tenant");
        }

        if (userContactRepository.findByUserIdAndContactId(userId, contactId).isPresent()) {
            throw new IllegalArgumentException("Contact is already assigned to this user");
        }

        // Set default: remove default flag from other contacts
        if (Boolean.TRUE.equals(isDefault)) {
            userContactRepository.findDefaultByUserId(userId)
                .ifPresent(existing -> {
                    existing.setIsDefault(false);
                    userContactRepository.save(existing);
                });
        }

        // Set authentication: remove auth flag from other contacts
        if (Boolean.TRUE.equals(isForAuthentication)) {
            if (!contact.canBeUsedForAuthentication()) {
                throw new IllegalArgumentException("Contact must be verified EMAIL or PHONE for authentication");
            }

            userContactRepository.findAuthenticationContactByUserId(userId)
                .ifPresent(existing -> {
                    existing.setIsForAuthentication(false);
                    userContactRepository.save(existing);
                });
        }

        UserContact userContact = UserContact.builder()
            .userId(userId)
            .contactId(contactId)
            .isDefault(isDefault != null ? isDefault : false)
            .isForAuthentication(isForAuthentication != null ? isForAuthentication : false)
            .build();

        return userContactRepository.save(userContact);
    }

    @Transactional
    public void removeContact(UUID userId, UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Removing contact from user: tenantId={}, userId={}, contactId={}", tenantId, userId, contactId);

        UserContact userContact = userContactRepository.findByUserIdAndContactId(userId, contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact assignment not found"));

        userContactRepository.delete(userContact);
    }

    @Transactional
    public UserContact setAsDefault(UUID userId, UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Setting default contact: tenantId={}, userId={}, contactId={}", tenantId, userId, contactId);

        UserContact userContact = userContactRepository.findByUserIdAndContactId(userId, contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact assignment not found"));

        // Remove default from others
        userContactRepository.findDefaultByUserId(userId)
            .ifPresent(existing -> {
                if (!existing.getContactId().equals(contactId)) {
                    existing.setIsDefault(false);
                    userContactRepository.save(existing);
                }
            });

        userContact.setAsDefault();
        return userContactRepository.save(userContact);
    }

    @Transactional
    public UserContact enableForAuthentication(UUID userId, UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Enabling contact for authentication: tenantId={}, userId={}, contactId={}", 
            tenantId, userId, contactId);

        UserContact userContact = userContactRepository.findByUserIdAndContactId(userId, contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact assignment not found"));

        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        if (!contact.canBeUsedForAuthentication()) {
            throw new IllegalArgumentException("Contact must be verified EMAIL or PHONE for authentication");
        }

        // Remove auth from others
        userContactRepository.findAuthenticationContactByUserId(userId)
            .ifPresent(existing -> {
                if (!existing.getContactId().equals(contactId)) {
                    existing.disableForAuthentication();
                    userContactRepository.save(existing);
                }
            });

        userContact.enableForAuthentication();
        return userContactRepository.save(userContact);
    }
}

