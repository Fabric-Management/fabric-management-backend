package com.fabricmanagement.common.platform.admin.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.admin.app.PlatformAdminService;
import com.fabricmanagement.common.platform.admin.dto.TenantStatistics;
import com.fabricmanagement.common.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.common.platform.tenant.dto.TenantDto;
import com.fabricmanagement.common.platform.user.dto.UserDto;
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
 * <p><b>Security:</b> All endpoints require PLATFORM_ADMIN role.
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
 * <h2>Tenant Context Switching:</h2>
 *
 * <p>Platform admin can switch tenant context using query parameter:
 *
 * <pre>
 * GET /api/admin/tenants/{tenantId}/users?tenantId={targetTenantId}
 * </pre>
 *
 * <p>Or use X-Tenant-Id header for automatic context switching (if implemented in interceptor).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class PlatformAdminController {

  private final PlatformAdminService platformAdminService;

  /**
   * Get all tenants in the system.
   *
   * <p><b>Security:</b> PLATFORM_ADMIN role required
   *
   * @return List of all tenants
   */
  @GetMapping("/tenants")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<List<TenantDto>>> getAllTenants() {
    log.info("Platform admin: Listing all tenants");

    List<TenantDto> tenants = platformAdminService.getAllTenants();

    return ResponseEntity.ok(
        ApiResponse.success(tenants, String.format("Found %d tenants", tenants.size())));
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
   * @param companyId The organization ID (path kept as companyId for backward compat)
   * @return Organization details
   */
  @GetMapping("/tenants/{tenantId}/companies/{companyId}")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<OrganizationDto>> getTenantCompany(
      @PathVariable UUID tenantId, @PathVariable UUID companyId) {
    log.info("Platform admin: Getting organization {} from tenant {}", companyId, tenantId);

    OrganizationDto organization = platformAdminService.getTenantOrganization(tenantId, companyId);

    return ResponseEntity.ok(ApiResponse.success(organization));
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

  /**
   * Switch tenant context for subsequent operations.
   *
   * <p><b>Note:</b> This is informational - actual tenant switching happens automatically via
   * TenantContext.executeInTenantContext() in service methods.
   *
   * @param tenantId The tenant ID to switch to
   * @return Success message
   */
  @PostMapping("/tenants/{tenantId}/switch")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<String>> switchTenantContext(@PathVariable UUID tenantId) {
    log.info("Platform admin: Switching tenant context to: {}", tenantId);

    // Validate tenant exists
    platformAdminService.getTenant(tenantId);

    // Note: Actual tenant switching is handled per-request via
    // TenantContext.executeInTenantContext()
    // This endpoint is just for validation/logging

    return ResponseEntity.ok(
        ApiResponse.success(
            "Tenant context switch ready. Use tenant-specific endpoints with tenantId path parameter."));
  }
}
