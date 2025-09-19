package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.common.core.application.dto.PageRequest;
import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.contact.application.dto.contact.request.CreateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.ContactDetailResponse;
import com.fabricmanagement.contact.application.dto.contact.response.ContactResponse;
import com.fabricmanagement.contact.domain.exception.ContactNotFoundException;
import com.fabricmanagement.contact.domain.valueobject.ContactStatus;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import com.fabricmanagement.contact.infrastructure.integration.company.CompanyDto;
import com.fabricmanagement.contact.infrastructure.integration.company.CompanyServiceClient;
import com.fabricmanagement.contact.infrastructure.persistence.entity.CompanyContactEntity;
import com.fabricmanagement.contact.infrastructure.persistence.repository.ContactJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing company contacts.
 * Handles all contact operations specific to companies.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CompanyContactService {

    private final ContactJpaRepository contactRepository;
    private final CompanyServiceClient companyServiceClient;
    private final ContactEmailService emailService;
    private final ContactPhoneService phoneService;
    private final ContactAddressService addressService;

    /**
     * Creates a new contact for a company.
     */
    public ContactDetailResponse createCompanyContact(UUID companyId, CreateContactRequest request) {
        log.debug("Creating contact for company: {}", companyId);

        // Verify company exists
        CompanyDto company = companyServiceClient.getCompanyById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));

        // Check if contact already exists for this company
        if (contactRepository.existsByCompanyId(companyId)) {
            throw new IllegalStateException("Contact already exists for company: " + companyId);
        }

        // Create company contact entity
        CompanyContactEntity contact = CompanyContactEntity.createForCompany(
            companyId,
            company.getTenantId(),
            company.getName()
        );
        contact.setContactType(request.getContactType() != null ? request.getContactType() : ContactType.CUSTOMER);
        contact.setStatus(ContactStatus.ACTIVE);

        // Set company-specific fields
        if (request.getIndustry() != null) {
            contact.setIndustry(request.getIndustry());
        }
        if (request.getCompanySize() != null) {
            contact.setCompanySize(request.getCompanySize());
        }
        if (request.getWebsite() != null) {
            contact.setWebsite(request.getWebsite());
        }
        if (request.getTaxId() != null) {
            contact.setTaxId(request.getTaxId());
        }
        if (request.getMainContactPerson() != null) {
            contact.setMainContactPerson(request.getMainContactPerson());
        }
        if (request.getMainContactEmail() != null) {
            contact.setMainContactEmail(request.getMainContactEmail());
        }
        if (request.getMainContactPhone() != null) {
            contact.setMainContactPhone(request.getMainContactPhone());
        }

        // Save contact
        contact = contactRepository.save(contact);

        // Add emails if provided
        if (request.getEmails() != null && !request.getEmails().isEmpty()) {
            emailService.addEmailsToContact(contact.getId(), request.getEmails());
        }

        // Add phones if provided
        if (request.getPhones() != null && !request.getPhones().isEmpty()) {
            phoneService.addPhonesToContact(contact.getId(), request.getPhones());
        }

        // Add addresses if provided
        if (request.getAddresses() != null && !request.getAddresses().isEmpty()) {
            addressService.addAddressesToContact(contact.getId(), request.getAddresses());
        }

        log.info("Contact created successfully for company: {}", companyId);
        return toDetailResponse(contact);
    }

    /**
     * Gets a company's contact by company ID.
     */
    @Transactional(readOnly = true)
    public ContactDetailResponse getCompanyContact(UUID companyId) {
        log.debug("Fetching contact for company: {}", companyId);

        CompanyContactEntity contact = contactRepository.findByCompanyId(companyId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for company: " + companyId));

        return toDetailResponse(contact);
    }

    /**
     * Updates a company's contact.
     */
    public ContactDetailResponse updateCompanyContact(UUID companyId, UpdateContactRequest request) {
        log.debug("Updating contact for company: {}", companyId);

        CompanyContactEntity contact = contactRepository.findByCompanyId(companyId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for company: " + companyId));

        // Update company-specific fields
        if (request.getIndustry() != null) {
            contact.setIndustry(request.getIndustry());
        }
        if (request.getCompanySize() != null) {
            contact.setCompanySize(request.getCompanySize());
        }
        if (request.getWebsite() != null) {
            contact.setWebsite(request.getWebsite());
        }
        if (request.getTaxId() != null) {
            contact.setTaxId(request.getTaxId());
        }
        if (request.getMainContactPerson() != null) {
            contact.setMainContactPerson(request.getMainContactPerson());
        }
        if (request.getMainContactEmail() != null) {
            contact.setMainContactEmail(request.getMainContactEmail());
        }
        if (request.getMainContactPhone() != null) {
            contact.setMainContactPhone(request.getMainContactPhone());
        }
        if (request.getBusinessHours() != null) {
            contact.setBusinessHours(request.getBusinessHours());
        }
        if (request.getPaymentTerms() != null) {
            contact.setPaymentTerms(request.getPaymentTerms());
        }
        if (request.getNotes() != null) {
            contact.setNotes(request.getNotes());
        }

        contact = contactRepository.save(contact);

        log.info("Contact updated successfully for company: {}", companyId);
        return toDetailResponse(contact);
    }

    /**
     * Deletes a company's contact (soft delete).
     */
    public void deleteCompanyContact(UUID companyId) {
        log.debug("Deleting contact for company: {}", companyId);

        CompanyContactEntity contact = contactRepository.findByCompanyId(companyId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for company: " + companyId));

        contact.markAsDeleted();
        contactRepository.save(contact);

        log.info("Contact deleted successfully for company: {}", companyId);
    }

    /**
     * Gets all company contacts for a tenant with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<ContactResponse> getCompanyContactsByTenant(UUID tenantId, PageRequest pageRequest) {
        log.debug("Fetching company contacts for tenant: {}", tenantId);

        Pageable pageable = pageRequest.toPageable();
        Page<CompanyContactEntity> contacts = contactRepository.findByTenantIdAndEntityType(tenantId, "COMPANY", pageable);

        return PageResponse.of(
            contacts.map(this::toResponse),
            pageRequest
        );
    }

    /**
     * Gets company contacts by industry.
     */
    @Transactional(readOnly = true)
    public PageResponse<ContactResponse> getCompanyContactsByIndustry(String industry, PageRequest pageRequest) {
        log.debug("Fetching company contacts for industry: {}", industry);

        Pageable pageable = pageRequest.toPageable();
        Page<CompanyContactEntity> contacts = contactRepository.findByIndustry(industry, pageable);

        return PageResponse.of(
            contacts.map(this::toResponse),
            pageRequest
        );
    }

    /**
     * Searches company contacts.
     */
    @Transactional(readOnly = true)
    public PageResponse<ContactResponse> searchCompanyContacts(String query, PageRequest pageRequest) {
        log.debug("Searching company contacts with query: {}", query);

        Pageable pageable = pageRequest.toPageable();
        Page<CompanyContactEntity> contacts = contactRepository.searchCompanyContacts(query, pageable);

        return PageResponse.of(
            contacts.map(this::toResponse),
            pageRequest
        );
    }

    /**
     * Updates credit information for a company.
     */
    public ContactDetailResponse updateCreditInfo(UUID companyId, Long creditLimit, String paymentTerms) {
        log.debug("Updating credit info for company: {}", companyId);

        CompanyContactEntity contact = contactRepository.findByCompanyId(companyId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for company: " + companyId));

        if (creditLimit != null) {
            contact.setCreditLimit(creditLimit);
        }
        if (paymentTerms != null) {
            contact.setPaymentTerms(paymentTerms);
        }

        contact = contactRepository.save(contact);

        log.info("Credit info updated for company: {}", companyId);
        return toDetailResponse(contact);
    }

    /**
     * Converts entity to detail response.
     */
    private ContactDetailResponse toDetailResponse(CompanyContactEntity entity) {
        ContactDetailResponse response = new ContactDetailResponse();
        response.setId(entity.getId());
        response.setContactType(entity.getContactType());
        response.setStatus(entity.getStatus());
        response.setNotes(entity.getNotes());

        // Add company-specific fields
        response.setCompanyId(entity.getCompanyId());
        response.setCompanyName(entity.getCompanyName());
        response.setIndustry(entity.getIndustry());
        response.setCompanySize(entity.getCompanySize());
        response.setWebsite(entity.getWebsite());
        response.setTaxId(entity.getTaxId());
        response.setMainContactPerson(entity.getMainContactPerson());
        response.setMainContactEmail(entity.getMainContactEmail());
        response.setMainContactPhone(entity.getMainContactPhone());

        // Load related data
        response.setEmails(emailService.getContactEmails(entity.getId()));
        response.setPhones(phoneService.getContactPhones(entity.getId()));
        response.setAddresses(addressService.getContactAddresses(entity.getId()));

        return response;
    }

    /**
     * Converts entity to response.
     */
    private ContactResponse toResponse(CompanyContactEntity entity) {
        ContactResponse response = new ContactResponse();
        response.setId(entity.getId());
        response.setContactType(entity.getContactType());
        response.setStatus(entity.getStatus());
        response.setCompanyId(entity.getCompanyId());
        response.setCompanyName(entity.getCompanyName());
        response.setIndustry(entity.getIndustry());
        response.setWebsite(entity.getWebsite());

        // Add primary contact info
        if (entity.getPrimaryEmail() != null) {
            response.setPrimaryEmail(entity.getPrimaryEmail().getEmail());
        }
        if (entity.getPrimaryPhone() != null) {
            response.setPrimaryPhone(entity.getPrimaryPhone().getFullPhoneNumber());
        }
        if (entity.getMainContactPerson() != null) {
            response.setMainContactPerson(entity.getMainContactPerson());
        }

        return response;
    }
}