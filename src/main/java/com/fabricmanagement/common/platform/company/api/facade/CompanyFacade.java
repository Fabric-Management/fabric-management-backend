package com.fabricmanagement.common.platform.company.api.facade;

import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.dto.CreateInitialSubscriptionsResult;
import com.fabricmanagement.common.platform.company.dto.CreateTenantCompanyRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company Facade - Internal API for cross-module communication.
 *
 * <p>Other modules should ONLY interact with Company module through this facade. This is IN-PROCESS
 * communication (no HTTP overhead).
 *
 * <h2>Usage Example:</h2>
 *
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

  /**
   * Check if any company has the given tax ID (global uniqueness for tenant onboarding).
   *
   * @param taxId tax ID
   * @return true if tax ID already exists
   */
  boolean existsByTaxId(String taxId);

  /**
   * Create root tenant company (tenant_id = company_id). Used only during tenant onboarding.
   *
   * @param request tenant company creation request
   * @return created company DTO with tenantId set to companyId
   */
  CompanyDto createTenantCompany(CreateTenantCompanyRequest request);

  /**
   * Resolve department ID by name for a company. Used during onboarding to assign admin user to a
   * department (e.g. when department name is provided and seed has already run).
   *
   * @param tenantId tenant ID
   * @param companyId company ID
   * @param departmentName department name
   * @return department ID if found
   */
  java.util.Optional<UUID> findDepartmentIdByName(
      UUID tenantId, UUID companyId, String departmentName);

  /**
   * Create initial OS subscriptions during tenant onboarding. Used only by Auth module.
   *
   * @param tenantId tenant ID
   * @param selectedOS list of OS codes (e.g. FabricOS); default FabricOS if empty
   * @param trialDays trial period in days
   * @return result with os codes and trial end
   */
  CreateInitialSubscriptionsResult createInitialSubscriptions(
      UUID tenantId, List<String> selectedOS, int trialDays);

  /**
   * Seed default departments and positions for a tenant company. Used only during onboarding.
   *
   * @param tenantId tenant ID
   * @param companyId company ID
   */
  void seedDepartmentsAndPositions(UUID tenantId, UUID companyId);

  /**
   * Add company address and contact from flat fields. Used during onboarding when sales-led request
   * includes address/contact. No-op if all null.
   *
   * @param companyId company ID
   * @param tenantId tenant ID
   * @param address street address (optional)
   * @param city city (optional)
   * @param country country (optional)
   * @param phoneNumber phone (optional)
   * @param email email (optional)
   */
  void assignCompanyAddressAndContact(
      UUID companyId,
      UUID tenantId,
      String address,
      String city,
      String country,
      String phoneNumber,
      String email);
}
