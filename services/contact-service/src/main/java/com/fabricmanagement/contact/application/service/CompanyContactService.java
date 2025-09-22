package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.application.dto.common.PageRequestDto;
import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.contact.application.dto.contact.request.CreateCompanyContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateCompanyContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.CompanyContactResponse;
import com.fabricmanagement.contact.application.dto.contact.response.ContactListResponse;
import com.fabricmanagement.contact.application.mapper.CompanyContactMapper;
import com.fabricmanagement.contact.domain.exception.ContactNotFoundException;
import com.fabricmanagement.contact.domain.model.CompanyContact;
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

import java.util.List;
import java.util.UUID;

/**
 * Service for managing company contacts.
 * Handles ONLY contact information - NO user profile or company business data.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CompanyContactService {

    private final ContactJpaRepository contactRepository;
    private final CompanyServiceClient companyServiceClient;
    private final CompanyContactMapper companyContactMapper;

    /**
     * Creates a new contact for a company.
     */
    public CompanyContactResponse createCompanyContact(UUID companyId, CreateCompanyContactRequest request) {
        log.debug("Creating contact for company: {}", companyId);

        // Verify company exists
        CompanyDto company = companyServiceClient.getCompanyById(companyId);
        if (company == null) {
            throw new IllegalArgumentException("Company not found: " + companyId);
        }

        // Check if contact already exists for this company
        if (contactRepository.existsByCompanyId(companyId)) {
            throw new IllegalStateException("Contact already exists for company: " + companyId);
        }

        // Create company contact domain model
        CompanyContact domain = companyContactMapper.toDomain(request);
        domain.setCompanyId(companyId);
        domain.setCompanyName(company.getName());
        domain.setTenantId(company.getTenantId());

        // Convert to entity and save
        CompanyContactEntity entity = companyContactMapper.toEntity(domain);
        entity = contactRepository.save(entity);

        // TODO: Handle contact details (emails, phones, addresses) when services are properly implemented

        log.info("Contact created successfully for company: {}", companyId);
        return companyContactMapper.toDetailResponse(entity);
    }

    /**
     * Gets a company's contact by company ID.
     */
    @Transactional(readOnly = true)
    public CompanyContactResponse getCompanyContact(UUID companyId) {
        log.debug("Fetching contact for company: {}", companyId);

        CompanyContactEntity entity = contactRepository.findByCompanyId(companyId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for company: " + companyId));

        return companyContactMapper.toDetailResponse(entity);
    }

    /**
     * Updates a company's contact.
     */
    public CompanyContactResponse updateCompanyContact(UUID companyId, UpdateCompanyContactRequest request) {
        log.debug("Updating contact for company: {}", companyId);

        CompanyContactEntity entity = contactRepository.findByCompanyId(companyId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for company: " + companyId));

        // Convert to domain, update, and convert back
        CompanyContact domain = companyContactMapper.toDomain(entity);
        companyContactMapper.updateDomain(request, domain);
        CompanyContactEntity updated = companyContactMapper.toEntity(domain);

        // Preserve entity metadata
        updated.setId(entity.getId());
        updated.setCompanyId(entity.getCompanyId());
        updated.setCompanyName(entity.getCompanyName());
        updated.setCreatedAt(entity.getCreatedAt());
        updated.setCreatedBy(entity.getCreatedBy());
        updated.setVersion(entity.getVersion());

        updated = contactRepository.save(updated);

        log.info("Contact updated successfully for company: {}", companyId);
        return companyContactMapper.toDetailResponse(updated);
    }

    /**
     * Deletes a company's contact (soft delete).
     */
    public void deleteCompanyContact(UUID companyId) {
        log.debug("Deleting contact for company: {}", companyId);

        CompanyContactEntity entity = contactRepository.findByCompanyId(companyId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for company: " + companyId));

        entity.markAsDeleted();
        contactRepository.save(entity);

        log.info("Contact deleted successfully for company: {}", companyId);
    }

    /**
     * Gets all company contacts for a tenant with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<ContactListResponse> getCompanyContactsByTenant(UUID tenantId, PageRequestDto pageRequest) {
        log.debug("Fetching company contacts for tenant: {}", tenantId);

        Pageable pageable = pageRequest.toPageable();
        Page<com.fabricmanagement.contact.infrastructure.persistence.entity.ContactEntity> contacts =
            contactRepository.findByTenantId(tenantId, pageable);

        List<ContactListResponse> content = contacts.getContent().stream()
            .map(entity -> companyContactMapper.toListResponse((CompanyContactEntity) entity))
            .collect(java.util.stream.Collectors.toList());

        return PageResponse.<ContactListResponse>builder()
            .content(content)
            .page(contacts.getNumber())
            .size(contacts.getSize())
            .totalElements(contacts.getTotalElements())
            .totalPages(contacts.getTotalPages())
            .first(contacts.isFirst())
            .last(contacts.isLast())
            .build();
    }

    /**
     * Searches company contacts.
     */
    @Transactional(readOnly = true)
    public PageResponse<ContactListResponse> searchCompanyContacts(String query, PageRequestDto pageRequest) {
        log.debug("Searching company contacts with query: {}", query);

        Pageable pageable = pageRequest.toPageable();
        Page<CompanyContactEntity> contacts = contactRepository.searchCompanyContacts(query, pageable);

        List<ContactListResponse> content = contacts.getContent().stream()
            .map(companyContactMapper::toListResponse)
            .collect(java.util.stream.Collectors.toList());

        return PageResponse.<ContactListResponse>builder()
            .content(content)
            .page(contacts.getNumber())
            .size(contacts.getSize())
            .totalElements(contacts.getTotalElements())
            .totalPages(contacts.getTotalPages())
            .first(contacts.isFirst())
            .last(contacts.isLast())
            .build();
    }
}