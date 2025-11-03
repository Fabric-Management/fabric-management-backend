package com.fabricmanagement.common.platform.admin.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.admin.dto.TenantStatistics;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import com.fabricmanagement.common.platform.company.infra.repository.SubscriptionRepository;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Platform Admin Service - Cross-tenant management operations.
 *
 * <p><b>CRITICAL:</b> This service allows PLATFORM_ADMIN users to access and manage
 * data across ALL tenants in the system.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>List all tenants in the system</li>
 *   <li>Access any tenant's data (companies, users, subscriptions, etc.)</li>
 *   <li>Manage tenant operations (activate, deactivate, etc.)</li>
 *   <li>Cross-tenant reporting and analytics</li>
 * </ul>
 *
 * <h2>Security:</h2>
 * <p>All methods MUST be called by users with PLATFORM_ADMIN role.
 * Tenant context switching is handled internally using TenantContext.executeInTenantContext().</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAdminService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Get all tenant companies in the system.
     *
     * <p><b>Platform Admin Only:</b> Returns all tenants regardless of tenant context.</p>
     * 
     * <p><b>Performance Optimization:</b> Uses database query instead of loading all
     * companies into memory and filtering in Java. This prevents system crashes when
     * there are thousands of companies.</p>
     *
     * @return List of all tenant companies
     */
    @Transactional(readOnly = true)
    public List<CompanyDto> getAllTenants() {
        log.info("Platform admin: Listing all tenants in system");

        // ✅ Query directly for root tenants instead of findAll() + filter
        List<Company> tenants = companyRepository.findRootTenants();

        log.info("Found {} tenants in system", tenants.size());

        return tenants.stream()
            .map(CompanyDto::from)
            .collect(Collectors.toList());
    }

    /**
     * Get tenant by ID (platform admin can access any tenant).
     *
     * @param tenantId The tenant ID to access
     * @return CompanyDto for the tenant
     */
    @Transactional(readOnly = true)
    public CompanyDto getTenant(UUID tenantId) {
        log.info("Platform admin: Accessing tenant: {}", tenantId);

        Company tenant = companyRepository.findById(tenantId)
            .filter(company -> company.getId().equals(company.getTenantId()))  // Must be root tenant
            .filter(Company::isTenant)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        return CompanyDto.from(tenant);
    }

    /**
     * Get all users in a specific tenant (platform admin access).
     *
     * <p>Executes query within the specified tenant's context.</p>
     *
     * @param tenantId The tenant ID to query
     * @return List of users in that tenant
     */
    @Transactional(readOnly = true)
    public List<UserDto> getTenantUsers(UUID tenantId) {
        log.info("Platform admin: Getting users for tenant: {}", tenantId);

        return TenantContext.executeInTenantContext(tenantId, () -> {
            List<User> users = userRepository.findByTenantIdAndIsActiveTrue(tenantId);

            log.debug("Found {} users in tenant {}", users.size(), tenantId);

            return users.stream()
                .map(UserDto::from)
                .collect(Collectors.toList());
        });
    }

    /**
     * Get all companies in a specific tenant (platform admin access).
     *
     * @param tenantId The tenant ID to query
     * @return List of companies in that tenant
     */
    @Transactional(readOnly = true)
    public List<CompanyDto> getTenantCompanies(UUID tenantId) {
        log.info("Platform admin: Getting companies for tenant: {}", tenantId);

        return TenantContext.executeInTenantContext(tenantId, () -> {
            List<Company> companies = companyRepository.findByTenantIdAndIsActiveTrue(tenantId);

            log.debug("Found {} companies in tenant {}", companies.size(), tenantId);

            return companies.stream()
                .map(CompanyDto::from)
                .collect(Collectors.toList());
        });
    }

    /**
     * Get user from any tenant (platform admin access).
     *
     * @param tenantId The tenant ID
     * @param userId The user ID
     * @return UserDto
     */
    @Transactional(readOnly = true)
    public UserDto getTenantUser(UUID tenantId, UUID userId) {
        log.info("Platform admin: Getting user {} from tenant {}", userId, tenantId);

        return TenantContext.executeInTenantContext(tenantId, () -> {
            User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("User %s not found in tenant %s", userId, tenantId)));

            return UserDto.from(user);
        });
    }

    /**
     * Get company from any tenant (platform admin access).
     *
     * @param tenantId The tenant ID
     * @param companyId The company ID
     * @return CompanyDto
     */
    @Transactional(readOnly = true)
    public CompanyDto getTenantCompany(UUID tenantId, UUID companyId) {
        log.info("Platform admin: Getting company {} from tenant {}", companyId, tenantId);

        return TenantContext.executeInTenantContext(tenantId, () -> {
            Company company = companyRepository.findByTenantIdAndId(tenantId, companyId)
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Company %s not found in tenant %s", companyId, tenantId)));

            return CompanyDto.from(company);
        });
    }

    /**
     * Execute any operation within a specific tenant context.
     *
     * <p>Utility method for platform admin to execute operations in any tenant context.</p>
     *
     * @param tenantId The tenant ID to switch to
     * @param operation The operation to execute
     * @param <T> Return type
     * @return Result of the operation
     */
    public <T> T executeInTenant(UUID tenantId, java.util.function.Supplier<T> operation) {
        log.debug("Platform admin: Executing operation in tenant context: {}", tenantId);
        return TenantContext.executeInTenantContext(tenantId, operation);
    }

    /**
     * Get tenant statistics.
     *
     * @param tenantId The tenant ID
     * @return TenantStatistics with counts
     */
    @Transactional(readOnly = true)
    public TenantStatistics getTenantStatistics(UUID tenantId) {
        log.info("Platform admin: Getting statistics for tenant: {}", tenantId);

        // Get tenant company info first
        Company tenantCompany = companyRepository.findById(tenantId)
            .filter(company -> company.getId().equals(company.getTenantId()))
            .filter(Company::isTenant)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        return TenantContext.executeInTenantContext(tenantId, () -> {
            long userCount = userRepository.countByTenantIdAndIsActiveTrue(tenantId);
            long companyCount = companyRepository.countByTenantIdAndIsActiveTrue(tenantId);
            
            // ✅ Performance: Use database-level count query instead of loading all subscriptions
            // This implements the same logic as Subscription.isActive() but at database level
            long subscriptionCount = subscriptionRepository.countActiveSubscriptionsByTenantId(
                tenantId, Instant.now());

            return TenantStatistics.builder()
                .tenantId(tenantId)
                .tenantUid(tenantCompany.getUid())
                .companyName(tenantCompany.getCompanyName())
                .userCount(userCount)
                .companyCount(companyCount)
                .subscriptionCount(subscriptionCount)
                .isActive(tenantCompany.getIsActive())
                .build();
        });
    }
}

