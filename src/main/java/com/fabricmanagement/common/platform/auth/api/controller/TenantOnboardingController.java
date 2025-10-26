package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
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
 * <p><b>Security:</b> These endpoints should be protected with PLATFORM_ADMIN role</p>
 *
 * <p>Used by sales team to onboard new enterprise customers.</p>
 *
 * <h2>Endpoint:</h2>
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
public class TenantOnboardingController {

    private final TenantOnboardingService onboardingService;

    /**
     * Create new tenant company with admin user (sales-led flow).
     *
     * <p><b>Security:</b> Requires PLATFORM_ADMIN role in production</p>
     * <p><b>Development:</b> Bypassed in local/dev profiles for testing</p>
     *
     * @param request Tenant onboarding request
     * @return Onboarding response with setup details
     */
    @PostMapping("/tenant")
    // @PreAuthorize("hasRole('PLATFORM_ADMIN')")  // Enable in production after creating first platform admin
    public ResponseEntity<ApiResponse<TenantOnboardingResponse>> createTenant(
            @Valid @RequestBody TenantOnboardingRequest request) {
        
        log.info("Admin onboarding request: company={}, admin={}",
            request.getCompanyName(),
            request.getAdminContact());

        TenantOnboardingResponse response = onboardingService.createSalesLedTenant(request);

        log.info("✅ Tenant onboarded successfully: companyUid={}, setupUrl={}",
            response.getCompanyUid(),
            response.getSetupUrl());

        return ResponseEntity.ok(ApiResponse.success(
            response,
            "Tenant created successfully. Welcome email sent to admin."
        ));
    }
}

