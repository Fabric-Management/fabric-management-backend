package com.fabricmanagement.platform.admin.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.admin.app.PlatformAdminService;
import com.fabricmanagement.platform.admin.dto.PlatformTenantSummary;
import com.fabricmanagement.platform.admin.dto.TenantStatistics;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.dto.UserDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Platform Admin Controller - Cross-tenant management endpoints.
 *
 * <p><b>Security:</b> All endpoints require platform staff: the PLATFORM_ADMIN role held in the
 * platform (Longward) tenant (PLATFORM-BOUNDARY-1).
 *
 * <p><b>Purpose:</b> Allows platform administrators to:
 *
 * <ul>
 *   <li>View all tenants in the system
 *   <li>Access any tenant's data (companies, users, etc.)
 *   <li>Manage tenant operations
 *   <li>Cross-tenant reporting
 * </ul>
 *
 * <p>Every read of one tenant runs as that tenant, so row-level security still applies; there is no
 * "switch tenant" state.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Platform Admin", description = "Platform Admin operations")
public class PlatformAdminController {

  private final PlatformAdminService platformAdminService;

  /**
   * Every active tenant with what it is to the platform and the screen section it belongs to
   * (PLATFORM-BOUNDARY-1): customers, Playground copies, and the platform's own tenants.
   *
   * <p><b>Security:</b> platform staff only (the platform role in the platform tenant).
   *
   * @return all active tenants, customers first
   */
  @GetMapping("/tenants")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<List<PlatformTenantSummary>>> listTenants() {
    return ResponseEntity.ok(ApiResponse.success(platformAdminService.listTenants()));
  }

  /**
   * Get tenant details by tenant ID.
   *
   * <p><b>Security:</b> PLATFORM_ADMIN role required
   *
   * @param tenantId The tenant ID
   * @return Tenant details
   */
  @GetMapping("/tenants/{tenantId}")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<TenantDto>> getTenant(@PathVariable UUID tenantId) {
    log.info("Platform admin: Getting tenant: {}", tenantId);

    TenantDto tenant = platformAdminService.getTenant(tenantId);

    return ResponseEntity.ok(ApiResponse.success(tenant));
  }

  /**
   * Get all users in a specific tenant.
   *
   * <p><b>Security:</b> PLATFORM_ADMIN role required
   *
   * <p><b>Tenant Context:</b> Automatically switches to target tenant context
   *
   * @param tenantId The tenant ID to query
   * @return List of users in that tenant
   */
  @GetMapping("/tenants/{tenantId}/users")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<List<UserDto>>> getTenantUsers(@PathVariable UUID tenantId) {
    log.info("Platform admin: Getting users for tenant: {}", tenantId);

    List<UserDto> users = platformAdminService.getTenantUsers(tenantId);

    return ResponseEntity.ok(
        ApiResponse.success(users, String.format("Found %d users in tenant", users.size())));
  }

  /**
   * Get all organizations in a specific tenant.
   *
   * <p><b>Security:</b> PLATFORM_ADMIN role required
   *
   * @param tenantId The tenant ID to query
   * @return List of organizations in that tenant
   */
  @GetMapping("/tenants/{tenantId}/companies")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<List<OrganizationDto>>> getTenantCompanies(
      @PathVariable UUID tenantId) {
    log.info("Platform admin: Getting organizations for tenant: {}", tenantId);

    List<OrganizationDto> organizations = platformAdminService.getTenantOrganizations(tenantId);

    return ResponseEntity.ok(
        ApiResponse.success(
            organizations,
            String.format("Found %d organizations in tenant", organizations.size())));
  }

  /**
   * Get specific user from a tenant.
   *
   * <p><b>Security:</b> PLATFORM_ADMIN role required
   *
   * @param tenantId The tenant ID
   * @param userId The user ID
   * @return User details
   */
  @GetMapping("/tenants/{tenantId}/users/{userId}")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<UserDto>> getTenantUser(
      @PathVariable UUID tenantId, @PathVariable UUID userId) {
    log.info("Platform admin: Getting user {} from tenant {}", userId, tenantId);

    UserDto user = platformAdminService.getTenantUser(tenantId, userId);

    return ResponseEntity.ok(ApiResponse.success(user));
  }

  /**
   * Get specific organization from a tenant.
   *
   * <p><b>Security:</b> PLATFORM_ADMIN role required
   *
   * @param tenantId The tenant ID
   * @param organizationId The organization ID
   * @return Organization details
   */
  @GetMapping("/tenants/{tenantId}/organizations/{organizationId}")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<OrganizationDto>> getTenantOrganization(
      @PathVariable UUID tenantId, @PathVariable UUID organizationId) {
    log.info("Platform admin: Getting organization {} from tenant {}", organizationId, tenantId);

    OrganizationDto organization =
        platformAdminService.getTenantOrganization(tenantId, organizationId);

    return ResponseEntity.ok(ApiResponse.success(organization));
  }

  /**
   * Get specific organization (company) from a tenant.
   *
   * <p>Alias for /organizations/{organizationId} – frontend uses "companies" terminology.
   *
   * <p><b>Security:</b> PLATFORM_ADMIN role required
   *
   * @param tenantId The tenant ID
   * @param companyId The organization/company ID
   * @return Organization details
   */
  @GetMapping("/tenants/{tenantId}/companies/{companyId}")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<OrganizationDto>> getTenantCompany(
      @PathVariable UUID tenantId, @PathVariable UUID companyId) {
    return getTenantOrganization(tenantId, companyId);
  }

  /**
   * Get tenant statistics (user count, company count, etc.).
   *
   * <p><b>Security:</b> PLATFORM_ADMIN role required
   *
   * @param tenantId The tenant ID
   * @return Tenant statistics
   */
  @GetMapping("/tenants/{tenantId}/statistics")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<TenantStatistics>> getTenantStatistics(
      @PathVariable UUID tenantId) {
    log.info("Platform admin: Getting statistics for tenant: {}", tenantId);

    TenantStatistics stats = platformAdminService.getTenantStatistics(tenantId);

    return ResponseEntity.ok(ApiResponse.success(stats));
  }
}
