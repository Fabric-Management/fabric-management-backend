package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.communication.infra.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private final ContactRepository contactRepository;

    @Transactional
    public Contact createContact(String contactValue, ContactType contactType, 
                                 String label, Boolean isPersonal, UUID parentContactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Creating contact: tenantId={}, type={}, value={}", 
            tenantId, contactType, maskContactValue(contactValue));

        // Validate extension
        if (contactType == ContactType.PHONE_EXTENSION && parentContactId == null) {
            throw new IllegalArgumentException("PHONE_EXTENSION requires parentContactId");
        }

        if (contactType == ContactType.PHONE_EXTENSION) {
            Contact parent = contactRepository.findById(parentContactId)
                .orElseThrow(() -> new IllegalArgumentException("Parent contact not found"));
            
            if (parent.getContactType() != ContactType.PHONE) {
                throw new IllegalArgumentException("Parent contact must be of type PHONE");
            }
            
            if (!parent.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("Parent contact must belong to same tenant");
            }
        }

        Contact contact = Contact.builder()
            .contactValue(contactValue)
            .contactType(contactType)
            .label(label)
            .isPersonal(isPersonal != null ? isPersonal : true)
            .parentContactId(parentContactId)
            .isVerified(false)
            .isPrimary(false)
            .build();

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
        log.trace("Finding contact by value and type: tenantId={}, type={}, value={}", 
            tenantId, contactType, maskContactValue(contactValue));

        return contactRepository.findByTenantIdAndContactValueAndContactType(
            tenantId, contactValue, contactType);
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
            throw new IllegalArgumentException("Contact does not belong to current tenant");
        }

        contact.verify();
        return contactRepository.save(contact);
    }

    @Transactional
    public Contact setAsPrimary(UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Setting contact as primary: tenantId={}, contactId={}", tenantId, contactId);

        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        if (!contact.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Contact does not belong to current tenant");
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
            throw new IllegalArgumentException("Contact does not belong to current tenant");
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
}

