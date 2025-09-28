package com.fabricmanagement.company.domain.repository;

import com.fabricmanagement.company.domain.model.Company;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.domain.valueobject.Industry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository interface for Company entity.
 * Follows clean architecture principles - only domain concerns, no infrastructure details.
 * Infrastructure layer will implement this interface with specific technology (JPA, etc.)
 */
public interface CompanyRepository {

    /**
     * Saves a company entity.
     */
    Company save(Company company);

    /**
     * Finds a company by ID and tenant ID.
     */
    Optional<Company> findByIdAndTenantId(UUID companyId, UUID tenantId);

    /**
     * Finds a company by ID.
     */
    Optional<Company> findById(UUID companyId);

    /**
     * Finds all companies for a tenant with pagination.
     */
    Page<Company> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Finds active companies for a tenant with pagination.
     */
    Page<Company> findActiveCompaniesByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Finds companies by status for a tenant.
     */
    List<Company> findByTenantIdAndStatus(UUID tenantId, CompanyStatus status);

    /**
     * Finds companies by industry for a tenant.
     */
    List<Company> findByTenantIdAndIndustry(UUID tenantId, Industry industry);

    /**
     * Searches companies by name or description.
     */
    Page<Company> searchCompanies(UUID tenantId, String searchQuery, Pageable pageable);

    /**
     * Finds companies by registration number (for validation).
     */
    Optional<Company> findByTenantIdAndRegistrationNumber(UUID tenantId, String registrationNumber);

    /**
     * Finds companies by tax number (for validation).
     */
    Optional<Company> findByTenantIdAndTaxNumber(UUID tenantId, String taxNumber);

    /**
     * Finds subsidiary companies of a parent company.
     */
    List<Company> findSubsidiaries(UUID parentCompanyId, UUID tenantId);

    /**
     * Finds companies where the given company ID is in their subsidiaryIds set.
     */
    List<Company> findCompaniesBySubsidiaryId(UUID subsidiaryId, UUID tenantId);

    /**
     * Finds companies that have the specified employee ID.
     */
    List<Company> findCompaniesByEmployeeId(UUID employeeId, UUID tenantId);

    /**
     * Soft deletes a company by ID and tenant ID.
     */
    void deleteByIdAndTenantId(UUID companyId, UUID tenantId);

    /**
     * Checks if a company exists by registration number for a tenant.
     */
    boolean existsByTenantIdAndRegistrationNumber(UUID tenantId, String registrationNumber);

    /**
     * Checks if a company exists by tax number for a tenant.
     */
    boolean existsByTenantIdAndTaxNumber(UUID tenantId, String taxNumber);

    /**
     * Counts companies by status for a tenant.
     */
    long countByTenantIdAndStatus(UUID tenantId, CompanyStatus status);

    /**
     * Counts total companies for a tenant.
     */
    long countByTenantId(UUID tenantId);
}