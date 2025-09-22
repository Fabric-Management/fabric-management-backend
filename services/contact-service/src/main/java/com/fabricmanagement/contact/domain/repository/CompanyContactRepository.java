package com.fabricmanagement.contact.domain.repository;

import com.fabricmanagement.contact.domain.model.CompanyContact;
import com.fabricmanagement.contact.domain.valueobject.Industry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CompanyContact domain entities.
 * This is a domain interface - implementations should handle persistence details.
 */
public interface CompanyContactRepository {

    /**
     * Saves a company contact.
     */
    CompanyContact save(CompanyContact companyContact);

    /**
     * Finds a company contact by its ID.
     */
    Optional<CompanyContact> findById(UUID id);

    /**
     * Finds a company contact by company ID.
     */
    Optional<CompanyContact> findByCompanyId(UUID companyId);

    /**
     * Finds all company contacts by tenant ID.
     */
    List<CompanyContact> findByTenantId(UUID tenantId);

    /**
     * Finds company contacts by industry.
     */
    List<CompanyContact> findByIndustry(Industry industry);

    /**
     * Finds company contacts by industry and tenant.
     */
    List<CompanyContact> findByIndustryAndTenantId(Industry industry, UUID tenantId);

    /**
     * Checks if a contact exists for the given company ID.
     */
    boolean existsByCompanyId(UUID companyId);

    /**
     * Searches company contacts by query string.
     */
    List<CompanyContact> searchByQuery(String query, UUID tenantId);

    /**
     * Finds company contacts with credit limit above the specified amount.
     */
    List<CompanyContact> findByCreditLimitGreaterThan(Long amount, UUID tenantId);

    /**
     * Finds all active company contacts.
     */
    List<CompanyContact> findActiveContacts(UUID tenantId);

    /**
     * Deletes a company contact by ID.
     */
    void deleteById(UUID id);
}