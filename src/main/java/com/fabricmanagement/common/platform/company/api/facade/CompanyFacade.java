package com.fabricmanagement.common.platform.company.api.facade;

import com.fabricmanagement.common.platform.company.dto.CompanyDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company Facade - Internal API for cross-module communication.
 *
 * <p>Other modules should ONLY interact with Company module through this facade.
 * This is IN-PROCESS communication (no HTTP overhead).</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class UserService {
 *     private final CompanyFacade companyFacade;  // In-process call
 *
 *     public void validateUserCompany(UUID companyId) {
 *         Optional<CompanyDto> company = companyFacade.findById(tenantId, companyId);
 *         // Use company data
 *     }
 * }
 * }</pre>
 */
public interface CompanyFacade {

    /**
     * Find company by ID.
     *
     * @param tenantId the tenant ID
     * @param companyId the company ID
     * @return company DTO if found
     */
    Optional<CompanyDto> findById(UUID tenantId, UUID companyId);

    /**
     * Get all active companies for tenant.
     *
     * @param tenantId the tenant ID
     * @return list of companies
     */
    List<CompanyDto> findByTenant(UUID tenantId);

    /**
     * Check if company exists.
     *
     * @param tenantId the tenant ID
     * @param companyId the company ID
     * @return true if exists
     */
    boolean exists(UUID tenantId, UUID companyId);
}

