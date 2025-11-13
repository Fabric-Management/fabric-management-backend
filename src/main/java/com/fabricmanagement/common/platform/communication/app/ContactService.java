package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.util.PhoneValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Contact Service - Business logic for contact management.
 *
 * <p>Handles CRUD operations for Contact entities with verification support.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Contact creation and validation</li>
 *   <li>Contact verification status management</li>
 *   <li>Primary contact designation</li>
 *   <li>Extension phone management</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^[0-9]{1,10}$");

    private final ContactRepository contactRepository;
    private final UserContactService userContactService;
    private final AuthUserRepository authUserRepository;

    @Transactional
    public Contact createContact(String contactValue, ContactType contactType,
                                 String label, Boolean isPersonal, UUID parentContactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        ContactType resolvedType = contactType != null ? contactType : inferContactType(contactValue);
        String normalizedValue = normalizeContactValue(contactValue, resolvedType);
        log.debug("Creating contact: tenantId={}, type={}, value={}",
            tenantId, resolvedType, maskContactValue(normalizedValue));

        validateContactValue(normalizedValue, resolvedType);

        if (resolvedType == ContactType.PHONE_EXTENSION && parentContactId == null) {
            throw new DomainException("PHONE_EXTENSION requires parentContactId");
        }

        if (resolvedType == ContactType.PHONE_EXTENSION) {
            Contact parent = contactRepository.findById(parentContactId)
                .orElseThrow(() -> new DomainException("Parent contact not found"));

            if (!parent.getContactType().isLandline()) {
                throw new DomainException("Parent contact must be of type LANDLINE");
            }

            if (!parent.getTenantId().equals(tenantId)) {
                throw new DomainException("Parent contact must belong to same tenant");
            }
        }

        Optional<Contact> existing = contactRepository
            .findByTenantIdAndContactValueAndContactType(tenantId, normalizedValue, resolvedType);
        if (existing.isPresent()) {
            Contact existingContact = existing.get();
            applyWhatsAppRules(existingContact);
            return existingContact;
        }

        Contact contact = Contact.builder()
            .contactValue(normalizedValue)
            .contactType(resolvedType)
            .label(label)
            .isPersonal(isPersonal != null ? isPersonal : true)
            .parentContactId(parentContactId)
            .isVerified(false)
            .isPrimary(false)
            .build();

        applyWhatsAppRules(contact);

        return contactRepository.save(contact);
    }

    @Transactional(readOnly = true)
    public Optional<Contact> findById(UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.trace("Finding contact: tenantId={}, contactId={}", tenantId, contactId);

        return contactRepository.findById(contactId)
            .filter(c -> c.getTenantId().equals(tenantId));
    }

    @Transactional(readOnly = true)
    public List<Contact> findByType(ContactType contactType) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.trace("Finding contacts by type: tenantId={}, type={}", tenantId, contactType);

        return contactRepository.findByTenantIdAndContactType(tenantId, contactType);
    }

    @Transactional(readOnly = true)
    public Optional<Contact> findByValueAndType(String contactValue, ContactType contactType) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        String normalizedValue = normalizeContactValue(contactValue, contactType);
        log.trace("Finding contact by value and type: tenantId={}, type={}, value={}",
            tenantId, contactType, maskContactValue(normalizedValue));

        return contactRepository.findByTenantIdAndContactValueAndContactType(
            tenantId, normalizedValue, contactType);
    }

    @Transactional(readOnly = true)
    public Optional<Contact> findByValue(String contactValue) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        String normalizedValue = contactValue != null ? contactValue.trim() : null;
        log.trace("Finding contact by value: tenantId={}, value={}", tenantId, maskContactValue(normalizedValue));
        return contactRepository.findByTenantIdAndContactValue(tenantId, normalizedValue);
    }

    /**
     * Check if any contact exists with the given email domain.
     * Used for providing context-aware error messages during login.
     * 
     * <p><b>Note:</b> This is a cross-tenant check (not tenant-scoped) because
     * we want to know if the domain exists anywhere in the system for better
     * error message context.</p>
     * 
     * @param domain Email domain (e.g., "gmail.com", "company.com")
     * @return true if any contact with this domain exists
     */
    @Transactional(readOnly = true)
    public boolean existsByEmailDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        String normalizedDomain = domain.trim().toLowerCase();
        log.trace("Checking if contacts exist with domain: {}", normalizedDomain);
        return contactRepository.existsByEmailDomain(normalizedDomain);
    }

    @Transactional(readOnly = true)
    public List<Contact> findExtensionsByParent(UUID parentContactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.trace("Finding extensions: tenantId={}, parentContactId={}", tenantId, parentContactId);

        return contactRepository.findExtensionsByParentContactId(tenantId, parentContactId);
    }

    @Transactional
    public Contact verifyContact(UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Verifying contact: tenantId={}, contactId={}", tenantId, contactId);

        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        if (!contact.getTenantId().equals(tenantId)) {
            throw new DomainException("Contact does not belong to current tenant");
        }

        String normalizedValue = normalizeContactValue(contact.getContactValue(), contact.getContactType());
        validateContactValue(normalizedValue, contact.getContactType());
        contact.setContactValue(normalizedValue);
        applyWhatsAppRules(contact);
        
        // Mark contact as verified
        contact.verify();
        Contact savedContact = contactRepository.save(contact);
        
        // ✅ If user has password, create AuthUser for this verified contact (multi-contact login support)
        createAuthUserForVerifiedContactIfUserHasPassword(savedContact);
        
        return savedContact;
    }

    @Transactional
    public Contact updateContact(Contact contact) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Updating contact: tenantId={}, contactId={}", tenantId, contact.getId());

        if (!contact.getTenantId().equals(tenantId)) {
            throw new DomainException("Contact does not belong to current tenant");
        }

        return contactRepository.save(contact);
    }

    @Transactional
    public Contact setAsPrimary(UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Setting contact as primary: tenantId={}, contactId={}", tenantId, contactId);

        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        if (!contact.getTenantId().equals(tenantId)) {
            throw new DomainException("Contact does not belong to current tenant");
        }

        // Remove primary flag from other contacts of same type
        List<Contact> sameTypeContacts = contactRepository.findByTenantIdAndContactType(
            tenantId, contact.getContactType());

        sameTypeContacts.forEach(c -> {
            if (!c.getId().equals(contactId) && c.getIsPrimary()) {
                c.removePrimary();
                contactRepository.save(c);
            }
        });

        contact.setAsPrimary();
        return contactRepository.save(contact);
    }

    @Transactional
    public void deleteContact(UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Deleting contact: tenantId={}, contactId={}", tenantId, contactId);

        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        if (!contact.getTenantId().equals(tenantId)) {
            throw new DomainException("Contact does not belong to current tenant");
        }

        contact.delete();
        contactRepository.save(contact);
    }

    private String maskContactValue(String contactValue) {
        if (contactValue == null) {
            return null;
        }
        // Simple masking for logging
        if (contactValue.contains("@")) {
            return contactValue.replaceAll("(.).*@.*", "$1***@***");
        }
        if (contactValue.startsWith("+")) {
            return contactValue.substring(0, 4) + "***";
        }
        return contactValue.length() > 4 ? contactValue.substring(0, 2) + "***" : "***";
    }

    private void applyWhatsAppRules(Contact contact) {
        if (contact.getContactType() == null) {
            contact.setIsWhatsApp(false);
            return;
        }

        if (!contact.getContactType().isMobile()) {
            if (Boolean.TRUE.equals(contact.getIsWhatsApp())) {
                throw new DomainException("WhatsApp is only supported for MOBILE contacts");
            }
            contact.setIsWhatsApp(false);
            return;
        }

        if (contact.getIsWhatsApp() == null) {
            contact.setIsWhatsApp(false);
        }
    }

    private void validateContactValue(String contactValue, ContactType contactType) {
        if (contactValue == null || contactValue.isBlank()) {
            throw new DomainException("Contact value cannot be empty");
        }
        if (contactType == null) {
            throw new DomainException("Contact type is required");
        }
        String trimmed = contactValue.trim();
        switch (contactType) {
            case EMAIL -> {
                if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
                    throw new DomainException("Invalid email format");
                }
            }
            case MOBILE -> {
                PhoneValidationUtil.ValidationResult result = PhoneValidationUtil.validateMobile(trimmed);
                if (!result.isValid()) {
                    throw new DomainException(result.getErrorMessage());
                }
            }
            case LANDLINE, FAX -> {
                PhoneValidationUtil.ValidationResult result = PhoneValidationUtil.validateLandline(trimmed);
                if (!result.isValid()) {
                    throw new DomainException(result.getErrorMessage());
                }
            }
            case PHONE_EXTENSION -> {
                if (!EXTENSION_PATTERN.matcher(trimmed).matches()) {
                    throw new DomainException("Phone extension must be 1-10 digits");
                }
            }
            default -> {
                // WEBSITE, SOCIAL_MEDIA left for future specialized validation
            }
        }
    }

    private String normalizeContactValue(String contactValue, ContactType contactType) {
        if (contactValue == null) {
            return null;
        }
        String trimmed = contactValue.trim();
        if (contactType != null && contactType.isPhone()) {
            String normalized = trimmed
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
            return normalized;
        }
        if (contactType == ContactType.WEBSITE) {
            return trimmed.toLowerCase();
        }
        return trimmed;
    }

    private ContactType inferContactType(String contactValue) {
        if (contactValue == null || contactValue.isBlank()) {
            throw new DomainException("Unable to infer contact type from empty value");
        }
        String trimmed = contactValue.trim();
        if (EMAIL_PATTERN.matcher(trimmed).matches()) {
            return ContactType.EMAIL;
        }
        if (PhoneValidationUtil.validateMobile(trimmed).isValid()) {
            return ContactType.MOBILE;
        }
        if (PhoneValidationUtil.validateLandline(trimmed).isValid()) {
            return ContactType.LANDLINE;
        }
        throw new DomainException("Unable to infer contact type from value: " + trimmed);
    }

    /**
     * Create AuthUser for verified contact if user has password (multi-contact login support).
     * 
     * <p>When a contact is verified, if the user already has a password (any AuthUser exists),
     * create AuthUser for this contact so user can login with any verified contact.</p>
     * 
     * <p>Same password is used for all contacts (password hash is copied from existing AuthUser).</p>
     */
    private void createAuthUserForVerifiedContactIfUserHasPassword(Contact contact) {
        try {
            // Find user who owns this contact
            List<com.fabricmanagement.common.platform.communication.domain.UserContact> userContacts = 
                userContactService.getUserContactsByContactId(contact.getId());
            
            if (userContacts.isEmpty()) {
                log.debug("Contact not assigned to any user, skipping AuthUser creation: contactId={}", 
                    contact.getId());
                return;
            }
            
            UUID userId = userContacts.get(0).getUserId();
            
            // ✅ Batch check: Get all user contact IDs and check if any have AuthUser
            List<com.fabricmanagement.common.platform.communication.domain.UserContact> allUserContacts = 
                userContactService.getUserContacts(userId);
            
            List<UUID> allContactIds = allUserContacts.stream()
                .map(com.fabricmanagement.common.platform.communication.domain.UserContact::getContactId)
                .toList();
            
            // ✅ Batch load: Get first existing AuthUser (if any) to copy password hash
            Optional<AuthUser> existingAuthUser = allContactIds.isEmpty()
                ? Optional.empty()
                : authUserRepository.findAllByContactIdIn(allContactIds).stream()
                    .findFirst();
            
            if (existingAuthUser.isPresent()) {
                // User has password - create AuthUser for this verified contact
                if (!authUserRepository.existsByContactId(contact.getId())) {
                    AuthUser newAuthUser = AuthUser.create(
                        contact.getId(),
                        existingAuthUser.get().getPasswordHash() // Same password hash
                    );
                    newAuthUser.setTenantId(contact.getTenantId());
                    newAuthUser.verify(); // Contact is verified, so AuthUser is verified
                    authUserRepository.save(newAuthUser);
                    
                    log.info("✅ AuthUser created for verified contact (multi-contact login): " +
                        "contactId={}, contactValue={}, userId={}",
                        contact.getId(), maskContactValue(contact.getContactValue()), userId);
                }
            } else {
                log.debug("User has no password yet, skipping AuthUser creation: userId={}, contactId={}", 
                    userId, contact.getId());
            }
        } catch (Exception e) {
            // Don't fail contact verification if AuthUser creation fails
            log.warn("Failed to create AuthUser for verified contact: contactId={}, error={}", 
                contact.getId(), e.getMessage());
        }
    }

    /**
     * Batch load contacts by IDs (avoids N+1 problem).
     * 
     * @param contactIds List of contact IDs
     * @return List of contacts (only from current tenant)
     */
    @Transactional(readOnly = true)
    public List<Contact> findAllById(List<UUID> contactIds) {
        if (contactIds == null || contactIds.isEmpty()) {
            return List.of();
        }
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.trace("Batch loading contacts: tenantId={}, count={}", tenantId, contactIds.size());
        return contactRepository.findAllById(contactIds).stream()
            .filter(contact -> contact.getTenantId().equals(tenantId))
            .toList();
    }
}

