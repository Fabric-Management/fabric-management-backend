package com.fabricmanagement.platform.subscription.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.subscription.app.TenantSeedService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for tenant seed operations.
 *
 * <p>Provides endpoints to seed default departments and positions for existing tenants.
 */
@RestController
@RequestMapping("/api/common/tenant-seed")
@RequiredArgsConstructor
@Slf4j
public class TenantSeedController {

  private final TenantSeedService tenantSeedService;
  private final OrganizationFacade organizationFacade;

  /**
   * Seed departments and positions for current tenant.
   *
   * <p><b>Idempotent:</b> If departments/positions already exist, they are skipped.
   *
   * <p><b>Use Case:</b> If a tenant was created before seed was implemented, or if
   * departments/positions were deleted, this endpoint can be used to seed default organizational
   * structure.
   *
   * @return Success message
   */
  @PostMapping("/departments-and-positions")
  public ResponseEntity<ApiResponse<String>> seedDepartmentsAndPositions() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID organizationId =
        organizationFacade
            .getRootOrganization()
            .map(o -> o.getId())
            .orElseThrow(() -> new IllegalArgumentException("Organization not found for tenant"));

    log.info("Manual seed request: tenantId={}, organizationId={}", tenantId, organizationId);

    // Check if already seeded
    if (tenantSeedService.isTenantSeeded(tenantId, organizationId)) {
      log.info("Tenant already seeded, skipping");
      return ResponseEntity.ok(
          ApiResponse.success("Tenant already has departments. No action taken."));
    }

    // Seed departments and positions
    tenantSeedService.seedDepartments(tenantId, organizationId);

    return ResponseEntity.ok(ApiResponse.success("Departments and positions seeded successfully"));
  }

  /**
   * Check if current tenant has been seeded.
   *
   * @return true if tenant has departments
   */
  @GetMapping("/check")
  public ResponseEntity<ApiResponse<Boolean>> checkIfSeeded() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID organizationId =
        organizationFacade
            .getRootOrganization()
            .map(o -> o.getId())
            .orElseThrow(() -> new IllegalArgumentException("Organization not found for tenant"));

    boolean isSeeded = tenantSeedService.isTenantSeeded(tenantId, organizationId);

    return ResponseEntity.ok(ApiResponse.success(isSeeded));
  }
}
