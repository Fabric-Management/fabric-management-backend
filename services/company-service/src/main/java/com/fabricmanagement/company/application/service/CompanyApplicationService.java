package com.fabricmanagement.company.application.service;

import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.common.core.domain.exception.EntityNotFoundException;
import com.fabricmanagement.common.security.context.SecurityContextUtil;
import com.fabricmanagement.company.application.dto.company.request.CreateCompanyRequest;
import com.fabricmanagement.company.application.dto.company.request.UpdateCompanyRequest;
import com.fabricmanagement.company.application.dto.company.response.CompanyDetailResponse;
import com.fabricmanagement.company.application.dto.company.response.CompanyResponse;
import com.fabricmanagement.company.application.mapper.CompanyMapper;
import com.fabricmanagement.company.domain.model.Company;
import com.fabricmanagement.company.domain.repository.CompanyRepository;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.infrastructure.integration.contact.ContactServiceClient;
import com.fabricmanagement.company.infrastructure.integration.user.UserServiceClient;
import com.fabricmanagement.company.infrastructure.messaging.publisher.CompanyEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Application service for company management operations with cross-service integration.
 * Orchestrates business logic and coordinates between domain and infrastructure layers.
 * Integrates with contact-service and user-service while maintaining clean boundaries.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CompanyApplicationService {

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final CompanyEventPublisher eventPublisher;
    private final ContactServiceClient contactServiceClient;
    private final UserServiceClient userServiceClient;

    /**
     * Creates a new company.
     */
    public CompanyDetailResponse createCompany(CreateCompanyRequest request) {
        log.info("Creating new company: {}", request.companyName());

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();

        // Validate unique constraints
        validateCompanyUniqueness(request, tenantId);

        // Create company domain entity
        Company company = companyMapper.toDomain(request);
        company.setTenantId(tenantId);
        company.setStatus(CompanyStatus.ACTIVE);

        // Save company
        Company savedCompany = companyRepository.save(company);

        // Publish company created event for other services to consume
        eventPublisher.publishCompanyCreatedEvent(savedCompany);

        log.info("Company created successfully with ID: {}", savedCompany.getId());
        return buildCompanyDetailResponse(savedCompany);
    }

    /**
     * Gets a company by ID with cross-service data.
     */
    @Transactional(readOnly = true)
    public CompanyDetailResponse getCompanyById(UUID companyId) {
        log.debug("Fetching company with ID: {}", companyId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Company company = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Company not found with ID: " + companyId));

        return buildCompanyDetailResponse(company);
    }

    /**
     * Updates an existing company.
     */
    public CompanyDetailResponse updateCompany(UUID companyId, UpdateCompanyRequest request) {
        log.info("Updating company with ID: {}", companyId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Company existingCompany = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Company not found with ID: " + companyId));

        // Validate unique constraints if changed
        validateCompanyUniquenessForUpdate(request, existingCompany, tenantId);

        // Update company using mapper
        companyMapper.updateDomainFromRequest(request, existingCompany);
        Company updatedCompany = companyRepository.save(existingCompany);

        // Publish company updated event
        eventPublisher.publishCompanyUpdatedEvent(updatedCompany);

        log.info("Company updated successfully with ID: {}", companyId);
        return buildCompanyDetailResponse(updatedCompany);
    }

    /**
     * Deactivates a company (soft delete).
     */
    public void deactivateCompany(UUID companyId) {
        log.info("Deactivating company with ID: {}", companyId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Company company = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Company not found with ID: " + companyId));

        company.deactivate();
        companyRepository.save(company);
        eventPublisher.publishCompanyDeletedEvent(company);

        log.info("Company deactivated successfully with ID: {}", companyId);
    }

    /**
     * Permanently deletes a company.
     */
    public void deleteCompany(UUID companyId) {
        log.info("Deleting company with ID: {}", companyId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        companyRepository.deleteByIdAndTenantId(companyId, tenantId);

        log.info("Company deleted successfully with ID: {}", companyId);
    }

    /**
     * Gets all companies for the current tenant with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> getCompanies(int page, int size, String sortBy, String sortDirection) {
        log.debug("Fetching companies - page: {}, size: {}, sort: {} {}", page, size, sortBy, sortDirection);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Company> companyPage = companyRepository.findByTenantId(tenantId, pageable);
        List<CompanyResponse> companyResponses = companyPage.getContent().stream()
            .map(companyMapper::toResponse)
            .toList();

        return PageResponse.<CompanyResponse>builder()
            .content(companyResponses)
            .page(companyPage.getNumber())
            .size(companyPage.getSize())
            .totalElements(companyPage.getTotalElements())
            .totalPages(companyPage.getTotalPages())
            .first(companyPage.isFirst())
            .last(companyPage.isLast())
            .build();
    }

    /**
     * Gets active companies for the current tenant with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> getActiveCompanies(int page, int size, String sortBy, String sortDirection) {
        log.debug("Fetching active companies - page: {}, size: {}, sort: {} {}", page, size, sortBy, sortDirection);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Company> companyPage = companyRepository.findActiveCompaniesByTenantId(tenantId, pageable);
        List<CompanyResponse> companyResponses = companyPage.getContent().stream()
            .map(companyMapper::toResponse)
            .toList();

        return PageResponse.<CompanyResponse>builder()
            .content(companyResponses)
            .page(companyPage.getNumber())
            .size(companyPage.getSize())
            .totalElements(companyPage.getTotalElements())
            .totalPages(companyPage.getTotalPages())
            .first(companyPage.isFirst())
            .last(companyPage.isLast())
            .build();
    }

    /**
     * Searches companies by name or description.
     */
    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> searchCompanies(String searchQuery, int page, int size) {
        log.debug("Searching companies with query: {}", searchQuery);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("companyName"));

        Page<Company> companyPage = companyRepository.searchCompanies(tenantId, searchQuery, pageable);
        List<CompanyResponse> companyResponses = companyPage.getContent().stream()
            .map(companyMapper::toResponse)
            .toList();

        return PageResponse.<CompanyResponse>builder()
            .content(companyResponses)
            .page(companyPage.getNumber())
            .size(companyPage.getSize())
            .totalElements(companyPage.getTotalElements())
            .totalPages(companyPage.getTotalPages())
            .first(companyPage.isFirst())
            .last(companyPage.isLast())
            .build();
    }

    /**
     * Activates a company.
     */
    public CompanyDetailResponse activateCompany(UUID companyId) {
        log.info("Activating company with ID: {}", companyId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Company company = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Company not found with ID: " + companyId));

        company.activate();
        Company activatedCompany = companyRepository.save(company);

        log.info("Company activated successfully with ID: {}", companyId);
        return buildCompanyDetailResponse(activatedCompany);
    }

    /**
     * Suspends a company.
     */
    public CompanyDetailResponse suspendCompany(UUID companyId) {
        log.info("Suspending company with ID: {}", companyId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Company company = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Company not found with ID: " + companyId));

        company.suspend();
        Company suspendedCompany = companyRepository.save(company);

        log.info("Company suspended successfully with ID: {}", companyId);
        return buildCompanyDetailResponse(suspendedCompany);
    }

    /**
     * Adds an employee to a company.
     */
    public CompanyDetailResponse addEmployeeToCompany(UUID companyId, UUID employeeId) {
        log.info("Adding employee {} to company {}", employeeId, companyId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Company company = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Company not found with ID: " + companyId));

        // Verify employee exists via user-service
        var userResponse = userServiceClient.userExists(employeeId);
        if (!userResponse.isSuccess() || !Boolean.TRUE.equals(userResponse.getData())) {
            throw new EntityNotFoundException("User not found with ID: " + employeeId);
        }

        company.addEmployee(employeeId);
        Company updatedCompany = companyRepository.save(company);

        log.info("Employee added successfully to company");
        return buildCompanyDetailResponse(updatedCompany);
    }

    /**
     * Removes an employee from a company.
     */
    public CompanyDetailResponse removeEmployeeFromCompany(UUID companyId, UUID employeeId) {
        log.info("Removing employee {} from company {}", employeeId, companyId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Company company = companyRepository.findByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Company not found with ID: " + companyId));

        company.removeEmployee(employeeId);
        Company updatedCompany = companyRepository.save(company);

        log.info("Employee removed successfully from company");
        return buildCompanyDetailResponse(updatedCompany);
    }

    /**
     * Builds a CompanyDetailResponse with cross-service data.
     */
    private CompanyDetailResponse buildCompanyDetailResponse(Company company) {
        CompanyDetailResponse response = companyMapper.toDetailResponse(company);

        // Fetch contact info from contact-service (with fallback)
        try {
            var contactResponse = contactServiceClient.getCompanyContact(company.getId());
            if (contactResponse.isSuccess() && contactResponse.getData() != null) {
                response.setContactInfo(contactResponse.getData());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch contact info for company {}: {}", company.getId(), e.getMessage());
        }

        // Fetch employee info from user-service (with fallback)
        if (!company.getEmployeeIds().isEmpty()) {
            try {
                var employeesResponse = userServiceClient.getUsersBasicInfo(company.getEmployeeIds().stream().toList());
                if (employeesResponse.isSuccess() && employeesResponse.getData() != null) {
                    response.setEmployees(employeesResponse.getData());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch employee info for company {}: {}", company.getId(), e.getMessage());
                response.setEmployees(Collections.emptyList());
            }
        }

        return response;
    }

    private void validateCompanyUniqueness(CreateCompanyRequest request, UUID tenantId) {
        if (request.registrationNumber() != null) {
            if (companyRepository.existsByTenantIdAndRegistrationNumber(tenantId, request.registrationNumber())) {
                throw new IllegalArgumentException("Company with registration number already exists: " + request.registrationNumber());
            }
        }

        if (request.taxNumber() != null) {
            if (companyRepository.existsByTenantIdAndTaxNumber(tenantId, request.taxNumber())) {
                throw new IllegalArgumentException("Company with tax number already exists: " + request.taxNumber());
            }
        }
    }

    private void validateCompanyUniquenessForUpdate(UpdateCompanyRequest request, Company existing, UUID tenantId) {
        if (request.registrationNumber() != null && !request.registrationNumber().equals(existing.getRegistrationNumber())) {
            if (companyRepository.existsByTenantIdAndRegistrationNumber(tenantId, request.registrationNumber())) {
                throw new IllegalArgumentException("Company with registration number already exists: " + request.registrationNumber());
            }
        }

        if (request.taxNumber() != null && !request.taxNumber().equals(existing.getTaxNumber())) {
            if (companyRepository.existsByTenantIdAndTaxNumber(tenantId, request.taxNumber())) {
                throw new IllegalArgumentException("Company with tax number already exists: " + request.taxNumber());
            }
        }
    }
}