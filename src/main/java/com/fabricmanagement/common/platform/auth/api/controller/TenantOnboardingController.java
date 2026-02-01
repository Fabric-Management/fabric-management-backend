package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant Onboarding Controller - Internal admin endpoints for tenant creation.
 *
 * <p><b>Security:</b> These endpoints should be protected with PLATFORM_ADMIN role
 *
 * <p>Used by sales team to onboard new enterprise customers.
 *
 * <h2>Endpoint:</h2>
 *
 * <pre>
 * POST /api/admin/onboarding/tenant
 * Authorization: Bearer {admin-token}
 *
 * Creates:
 * - New tenant company (tenant_id = company_id)
 * - Admin user (pre-approved)
 * - FabricOS subscription (mandatory, trial)
 * - Selected OS subscriptions (trial)
 * - Registration token (for password setup)
 *
 * Sends welcome email to admin with setup link.
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/onboarding")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Tenant Onboarding (Admin)",
    description =
        "Sales-led tenant creation. Creates tenant company, admin user, subscriptions, and sends welcome email with setup link. Requires platform admin in production.")
public class TenantOnboardingController {

  private final TenantOnboardingService onboardingService;

  @Operation(
      summary = "Create tenant (sales-led)",
      description =
          "Creates a new tenant company with tenant_id = company_id, admin user, selected OS subscriptions (trial), and registration token. Sends welcome email to admin with setup link. In production this endpoint should be protected with PLATFORM_ADMIN role.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Tenant created successfully; welcome email sent to admin",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = TenantOnboardingResponse.class)))
  @PostMapping("/tenant")
  // @PreAuthorize("hasRole('PLATFORM_ADMIN')")  // Enable in production after creating first
  // platform admin
  public ResponseEntity<ApiResponse<TenantOnboardingResponse>> createTenant(
      @Valid @RequestBody TenantOnboardingRequest request) {

    log.info(
        "Admin onboarding request: company={}, admin={}",
        request.getCompanyName(),
        request.getAdminContact());

    TenantOnboardingResponse response = onboardingService.createSalesLedTenant(request);

    log.info(
        "✅ Tenant onboarded successfully: companyUid={}, setupUrl={}",
        response.getCompanyUid(),
        response.getSetupUrl());

    return ResponseEntity.ok(
        ApiResponse.success(response, "Tenant created successfully. Welcome email sent to admin."));
  }
}
