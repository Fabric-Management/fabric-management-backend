package com.fabricmanagement.platform.admin.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.core.employee.app.EmployeeService;
import com.fabricmanagement.platform.admin.dto.TenantStatistics;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.subscription.infra.repository.SubscriptionRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform Admin Service - Cross-tenant management operations.
 *
 * <p><b>CRITICAL:</b> This service allows PLATFORM_ADMIN users to access and manage data across ALL
 * tenants in the system.
 *
 * <h2>Features:</h2>
 *
 * <ul>
 *   <li>List all tenants in the system
 *   <li>Access any tenant's data (companies, users, subscriptions, etc.)
 *   <li>Manage tenant operations (activate, deactivate, etc.)
 *   <li>Cross-tenant reporting and analytics
 * </ul>
 *
 * <h2>Security:</h2>
 *
 * <p>All methods MUST be called by users with PLATFORM_ADMIN role. Tenant context switching is
 * handled internally using TenantContext.executeInTenantContext().
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAdminService {

  private final TenantRepository tenantRepository;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final EmployeeService employeeService;

  /**
   * Get all tenants in the system (unpaginated).
   *
   * <p><b>Platform Admin Only:</b> Returns all tenants regardless of tenant context.
   *
   * @return List of all tenants
   */
  @Transactional(readOnly = true)
  public List<TenantDto> getAllTenants() {
    log.info("Platform admin: Listing all tenants in system");

    List<Tenant> tenants = tenantRepository.findAllActive();

    log.info("Found {} tenants in system", tenants.size());

    return tenants.stream().map(TenantDto::from).collect(Collectors.toList());
  }

  /**
   * Get all tenants in the system with pagination.
   *
   * <p><b>Platform Admin Only:</b> Returns paginated tenants regardless of tenant context.
   *
   * @param pageable page, size and sort parameters
   * @return Page of tenants
   */
  @Transactional(readOnly = true)
  public Page<TenantDto> getAllTenants(Pageable pageable) {
    log.info(
        "Platform admin: Listing tenants (page={}, size={})",
        pageable.getPageNumber(),
        pageable.getPageSize());

    Page<Tenant> page = tenantRepository.findAllActive(pageable);

    log.debug("Found {} tenants on page {}", page.getNumberOfElements(), page.getNumber());

    return page.map(TenantDto::from);
  }

  /**
   * Get tenant by ID (platform admin can access any tenant).
   *
   * @param tenantId The tenant ID to access
   * @return TenantDto for the tenant
   */
  @Transactional(readOnly = true)
  public TenantDto getTenant(UUID tenantId) {
    log.info("Platform admin: Accessing tenant: {}", tenantId);

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

    return TenantDto.from(tenant);
  }

  /**
   * Get all users in a specific tenant (platform admin access).
   *
   * <p>Executes query within the specified tenant's context.
   *
   * @param tenantId The tenant ID to query
   * @return List of users in that tenant
   */
  @Transactional(readOnly = true)
  public List<UserDto> getTenantUsers(UUID tenantId) {
    log.info("Platform admin: Getting users for tenant: {}", tenantId);

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          List<User> users = userRepository.findByTenantIdAndIsActiveTrue(tenantId);

          log.debug("Found {} users in tenant {}", users.size(), tenantId);

          return users.stream().map(UserDto::from).collect(Collectors.toList());
        });
  }

  /**
   * Get all organizations in a specific tenant (platform admin access).
   *
   * @param tenantId The tenant ID to query
   * @return List of organizations in that tenant
   */
  @Transactional(readOnly = true)
  public List<OrganizationDto> getTenantOrganizations(UUID tenantId) {
    log.info("Platform admin: Getting organizations for tenant: {}", tenantId);

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          List<Organization> orgs = organizationRepository.findByTenantIdAndIsActiveTrue(tenantId);

          log.debug("Found {} organizations in tenant {}", orgs.size(), tenantId);

          return orgs.stream().map(OrganizationDto::from).collect(Collectors.toList());
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

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          User user =
              userRepository
                  .findByTenantIdAndId(tenantId, userId)
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException(
                              String.format("User %s not found in tenant %s", userId, tenantId)));

          return UserDto.from(
              user, employeeService.getEmployeeByUserId(tenantId, userId).orElse(null));
        });
  }

  /**
   * Get organization from any tenant (platform admin access).
   *
   * @param tenantId The tenant ID
   * @param organizationId The organization ID
   * @return OrganizationDto
   */
  @Transactional(readOnly = true)
  public OrganizationDto getTenantOrganization(UUID tenantId, UUID organizationId) {
    log.info("Platform admin: Getting organization {} from tenant {}", organizationId, tenantId);

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          Organization org =
              organizationRepository
                  .findByTenantIdAndId(tenantId, organizationId)
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException(
                              String.format(
                                  "Organization %s not found in tenant %s",
                                  organizationId, tenantId)));

          return OrganizationDto.from(org);
        });
  }

  /**
   * Execute any operation within a specific tenant context.
   *
   * <p>Utility method for platform admin to execute operations in any tenant context.
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

    // Get tenant info first
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          long userCount = userRepository.countByTenantIdAndIsActiveTrue(tenantId);
          long organizationCount = organizationRepository.countByTenantIdAndIsActiveTrue(tenantId);

          // Performance: Use database-level count query
          long subscriptionCount =
              subscriptionRepository.countActiveSubscriptionsByTenantId(tenantId, Instant.now());

          return TenantStatistics.builder()
              .tenantId(tenantId)
              .tenantUid(tenant.getUid())
              .companyName(tenant.getName())
              .userCount(userCount)
              .companyCount(organizationCount) // Now organization count
              .subscriptionCount(subscriptionCount)
              .isActive(tenant.getIsActive())
              .build();
        });
  }
}
