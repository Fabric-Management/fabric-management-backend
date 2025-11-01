package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.CompanyContact;
import com.fabricmanagement.common.platform.communication.infra.repository.CompanyContactRepository;
import com.fabricmanagement.common.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company Contact Service - Business logic for company-contact assignments.
 *
 * <p>Handles Many-to-Many relationship between Company and Contact.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Assign contacts to companies</li>
 *   <li>Remove company-contact assignments</li>
 *   <li>Manage default contact for business communication</li>
 *   <li>Manage department-specific contacts</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyContactService {

    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final CompanyContactRepository companyContactRepository;

    @Transactional(readOnly = true)
    public List<CompanyContact> getCompanyContacts(UUID companyId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding company contacts: tenantId={}, companyId={}", tenantId, companyId);

        return companyContactRepository.findByTenantIdAndCompanyId(tenantId, companyId);
    }

    @Transactional(readOnly = true)
    public Optional<CompanyContact> getDefaultContact(UUID companyId) {
        log.trace("Finding default contact: companyId={}", companyId);
        return companyContactRepository.findDefaultByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public List<CompanyContact> getDepartmentContacts(UUID companyId, String department) {
        log.trace("Finding department contacts: companyId={}, department={}", companyId, department);
        return companyContactRepository.findByCompanyIdAndDepartment(companyId, department);
    }

    @Transactional
    public CompanyContact assignContact(UUID companyId, UUID contactId, Boolean isDefault, String department) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Assigning contact to company: tenantId={}, companyId={}, contactId={}, isDefault={}, department={}",
            tenantId, companyId, contactId, isDefault, department);

        // Validate company exists
        companyRepository.findByTenantIdAndId(tenantId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        Contact contact = contactRepository.findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        if (!contact.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Contact does not belong to current tenant");
        }

        if (companyContactRepository.findByCompanyIdAndContactId(companyId, contactId).isPresent()) {
            throw new IllegalArgumentException("Contact is already assigned to this company");
        }

        // Set default: remove default flag from other contacts
        if (Boolean.TRUE.equals(isDefault)) {
            companyContactRepository.findDefaultByCompanyId(companyId)
                .ifPresent(existing -> {
                    existing.setIsDefault(false);
                    companyContactRepository.save(existing);
                });
        }

        CompanyContact companyContact = CompanyContact.builder()
            .companyId(companyId)
            .contactId(contactId)
            .isDefault(isDefault != null ? isDefault : false)
            .department(department)
            .build();

        return companyContactRepository.save(companyContact);
    }

    @Transactional
    public void removeContact(UUID companyId, UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Removing contact from company: tenantId={}, companyId={}, contactId={}", 
            tenantId, companyId, contactId);

        CompanyContact companyContact = companyContactRepository.findByCompanyIdAndContactId(companyId, contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact assignment not found"));

        companyContactRepository.delete(companyContact);
    }

    @Transactional
    public CompanyContact setAsDefault(UUID companyId, UUID contactId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Setting default contact: tenantId={}, companyId={}, contactId={}", tenantId, companyId, contactId);

        CompanyContact companyContact = companyContactRepository.findByCompanyIdAndContactId(companyId, contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact assignment not found"));

        // Remove default from others
        companyContactRepository.findDefaultByCompanyId(companyId)
            .ifPresent(existing -> {
                if (!existing.getContactId().equals(contactId)) {
                    existing.setIsDefault(false);
                    companyContactRepository.save(existing);
                }
            });

        companyContact.setAsDefault();
        return companyContactRepository.save(companyContact);
    }
}

