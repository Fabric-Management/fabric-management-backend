package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.common.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
 * Public Signup Controller - Self-service tenant registration.
 *
 * <p><b>Public endpoints</b> - No authentication required
 *
 * <p>Used by users signing up from the website.
 *
 * <h2>Flow:</h2>
 *
 * <pre>
 * 1. POST /api/public/signup
 *    → Creates tenant + company + admin user
 *    → Sends email with setup link (via SendWelcomeEmailStep in orchestrator)
 *
 * 2. User clicks email link
 *    → Redirects to /setup?token=xyz
 *    → Email verified by click (no verification code needed)
 *
 * 3. User sets password
 *    → POST /api/auth/setup-password (token + password only)
 *    → Auto-login → Dashboard (onboarding wizard if needed)
 * </pre>
 *
 * <p><b>Note:</b> Verification codes are NOT used in self-service signup. Email link click = email
 * verified. Verification codes are only used for unverified contacts during login flows.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Public Signup",
    description =
        "Self-service tenant registration. No authentication required. Creates tenant, company, admin user, FabricOS trial, and sends setup link by email. Email link click = email verified.")
public class PublicSignupController {

  private final TenantOnboardingService onboardingService;

  @Operation(
      summary = "Self-service signup",
      description =
          "Creates a new tenant from the public website. Company, admin user, and FabricOS trial are created; user receives an email with a setup link to set password. Terms must be accepted.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Registration initiated; user should check email for setup link",
      content = @Content(mediaType = "application/json"))
  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<String>> signup(@Valid @RequestBody SelfSignupRequest request) {

    log.info(
        "Public signup request: company={}, email={}",
        request.getCompanyName(),
        PiiMaskingUtil.maskEmail(request.getEmail()));

    // Orchestrator handles everything: tenant, org, user, subscriptions, token, and email
    TenantOnboardingResponse response = onboardingService.createSelfServiceTenant(request);

    log.info(
        "Self-service signup completed: companyUid={}, setupUrl={}",
        response.getCompanyUid(),
        response.getSetupUrl());

    return ResponseEntity.ok(
        ApiResponse.success(
            "Welcome! Check your email to complete registration.",
            "Registration initiated successfully"));
  }
}
