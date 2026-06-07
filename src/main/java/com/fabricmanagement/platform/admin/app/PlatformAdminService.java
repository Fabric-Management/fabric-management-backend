package com.fabricmanagement.platform.admin.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.admin.dto.TenantStatistics;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.subscription.infra.repository.SubscriptionRepository;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * <h2>RLS Protection Model:</h2>
 *
 * <p>This service uses JPA repositories with {@link TenantContext#executeInTenantContext(UUID,
 * java.util.function.Supplier)} to switch the active tenant before querying. The underlying
 * connection uses the {@code fabric_app} role (NOBYPASSRLS), meaning PostgreSQL RLS policies are
 * always enforced — admin can only see data belonging to the tenant they explicitly switch to.
 *
 * <p>This is intentionally NOT using {@code SystemTransactionExecutor} (BYPASSRLS). BYPASSRLS would
 * expose ALL tenants' data in a single query, which is unnecessarily broad for per-tenant admin
 * views. The current approach provides defense-in-depth: even if the admin code has a bug, RLS
 * prevents cross-tenant data leakage.
 *
 * <p>Tenant listing (cross-tenant) operations delegate to {@link TenantSystemService} which
 * correctly uses BYPASSRLS for the tenant table's self-row RLS policy.
 *
 * <h2>Security:</h2>
 *
 * <p>All methods MUST be called by users with PLATFORM_ADMIN role (enforced by
 * {@code @PreAuthorize} on the controller). Tenant existence is validated before context switch.
 *
 * @see com.fabricmanagement.common.infrastructure.persistence.TenantContext
 * @see TenantSystemService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAdminService {

  private final TenantSystemService tenantSystemService;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final EmployeeProjectionPort employeeProjectionPort;

  /**
   * Get all tenants in the system (unpaginated).
   *
   * <p><b>Platform Admin Only:</b> Returns all tenants regardless of tenant context.
   *
   * @return List of all tenants
   */
  public List<TenantDto> getAllTenants() {
    log.info("Platform admin: Listing all tenants in system");

    List<TenantDto> tenants = tenantSystemService.getAllActive();

    log.info("Found {} tenants in system", tenants.size());

    return tenants;
  }

  /**
   * Get all tenants in the system with pagination.
   *
   * <p><b>Platform Admin Only:</b> Returns paginated tenants regardless of tenant context.
   *
   * @param pageable page, size and sort parameters
   * @return Page of tenants
   */
  public Page<TenantDto> getAllTenants(Pageable pageable) {
    log.info(
        "Platform admin: Listing tenants (page={}, size={})",
        pageable.getPageNumber(),
        pageable.getPageSize());

    // TenantSystemService.getAllActive() returns unpaginated List<TenantDto>.
    // For admin pagination, we do in-memory paging. Acceptable because tenant
    // count is O(hundreds) not O(millions). If this becomes a bottleneck,
    // add a paginated JDBC query to TenantSystemService.
    List<TenantDto> all = tenantSystemService.getAllActive();
    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), all.size());
    List<TenantDto> pageContent = start >= all.size() ? List.of() : all.subList(start, end);

    log.debug("Found {} tenants on page {}", pageContent.size(), pageable.getPageNumber());

    return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, all.size());
  }

  /**
   * Get tenant by ID (platform admin can access any tenant).
   *
   * @param tenantId The tenant ID to access
   * @return TenantDto for the tenant
   */
  public TenantDto getTenant(UUID tenantId) {
    log.info("Platform admin: Accessing tenant: {}", tenantId);

    return tenantSystemService
        .findById(tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
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
    validateTenantExists(tenantId);

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          List<User> users = userRepository.findByTenantIdAndIsActiveTrue(tenantId);
          if (users.isEmpty()) {
            return List.of();
          }

          log.debug("Found {} users in tenant {}", users.size(), tenantId);

          List<UUID> userIds = users.stream().map(User::getId).toList();
          Map<UUID, EmployeeSnapshot> employeeMap =
              employeeProjectionPort.findByUserIds(tenantId, userIds);

          return users.stream()
              .map(user -> UserDto.from(user, employeeMap.get(user.getId())))
              .collect(Collectors.toList());
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
    validateTenantExists(tenantId);

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
              user, employeeProjectionPort.findByUserId(tenantId, userId).orElse(null));
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
  public TenantStatistics getTenantStatistics(UUID tenantId) {
    log.info("Platform admin: Getting statistics for tenant: {}", tenantId);

    // Get tenant info via system executor (BYPASSRLS — tenant table has self-row RLS)
    TenantDto tenant =
        tenantSystemService
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
              .companyCount(organizationCount)
              .subscriptionCount(subscriptionCount)
              .isActive(tenant.getIsActive())
              .build();
        });
  }

  // ========================================
  // VALIDATION HELPERS
  // ========================================

  /**
   * Validate that the target tenant exists before performing cross-tenant operations.
   *
   * <p>Prevents empty-result confusion: without this, querying a non-existent tenant returns an
   * empty list (RLS filters everything out) rather than a clear 404.
   *
   * @param tenantId The tenant ID to validate
   * @throws IllegalArgumentException if tenant does not exist
   */
  private void validateTenantExists(UUID tenantId) {
    if (!tenantSystemService.exists(tenantId)) {
      throw new IllegalArgumentException("Tenant not found: " + tenantId);
    }
  }
}
