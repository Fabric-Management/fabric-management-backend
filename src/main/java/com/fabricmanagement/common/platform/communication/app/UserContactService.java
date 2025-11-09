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

    /**
     * Check if user-contact assignment exists.
     * 
     * <p><b>Performance:</b> Uses EXISTS query instead of loading all contacts.
     * This prevents N+1 problems when checking assignment status.</p>
     * 
     * @param userId the user ID
     * @param contactId the contact ID
     * @return true if contact is assigned to user
     */
    @Transactional(readOnly = true)
    public boolean existsUserContact(UUID userId, UUID contactId) {
        log.trace("Checking if user-contact assignment exists: userId={}, contactId={}", userId, contactId);
        return userContactRepository.existsByUserIdAndContactId(userId, contactId);
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
        boolean forAuth = Boolean.TRUE.equals(isForAuthentication);
        if (forAuth) {
            log.warn("isForAuthentication flag is deprecated and ignored. userId={}, contactId={}", userId, contactId);
        }

        UserContact userContact = UserContact.builder()
            .userId(userId)
            .contactId(contactId)
            .isDefault(isDefault != null ? isDefault : false)
            .build();

        return userContactRepository.save(userContact);
    }

    @Transactional
    public void removeContact(UUID userId, UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Removing contact from user: tenantId={}, userId={}, contactId={}", tenantId, userId, contactId);

        UserContact userContact = userContactRepository.findByUserIdAndContactId(userId, contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact assignment not found"));

        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        if (Boolean.TRUE.equals(contact.getIsVerified())) {
            long verifiedCount = userContactRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .map(uc -> contactRepository.findById(uc.getContactId()))
                .flatMap(Optional::stream)
                .filter(Contact::getIsVerified)
                .count();

            if (verifiedCount <= 1) {
                log.warn("Attempt to remove last verified contact prevented: tenantId={}, userId={}, contactId={}",
                    tenantId, userId, contactId);
                throw new IllegalStateException("Cannot remove the last verified contact. Add another verified contact first.");
            }
        }

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
    @Deprecated
    public UserContact enableForAuthentication(UUID userId, UUID contactId) {
        log.warn("enableForAuthentication is deprecated. userId={}, contactId={}", userId, contactId);
        return userContactRepository.findByUserIdAndContactId(userId, contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact assignment not found"));
    }
}

