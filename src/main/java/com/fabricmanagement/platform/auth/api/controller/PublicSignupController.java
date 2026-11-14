package com.fabricmanagement.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.logging.SensitiveLogData;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.rate.RateLimited;
import com.fabricmanagement.platform.auth.app.SignupRequestService;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
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
 * 1. POST /api/v1/public/signup
 *    → Stores a small pending signup and an unverified sales lead, mails a setup link.
 *      No tenant, users or demo data exist yet. The answer is the same for every e-mail address.
 *
 * 2. User clicks email link (valid 24 hours)
 *    → Redirects to /setup?token=xyz
 *    → Email verified by click (no verification code needed)
 *
 * 3. User sets password
 *    → POST /api/v1/auth/setup-password (token + password only)
 *    → Builds the company in one transaction, then auto-login → Dashboard
 * </pre>
 *
 * <p><b>Note:</b> Verification codes are NOT used in self-service signup. Email link click = email
 * verified. Verification codes are only used for unverified contacts during login flows.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Public Signup",
    description =
        "Self-service tenant registration. No authentication required. Stores a pending signup and sends a setup link by email; the tenant is built when the link is used. Email link click = email verified.")
public class PublicSignupController {

  private final SignupRequestService signupRequests;

  @Operation(
      summary = "Self-service signup",
      description =
          "Takes a signup from the public website. Nothing but a pending signup is stored until the emailed link is used; then the company, admin user and FabricOS trial are created. The answer is the same whatever the state of the e-mail address. Terms must be accepted.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Registration initiated; user should check email for setup link",
      content = @Content(mediaType = "application/json"))
  @PostMapping("/signup")
  @RateLimited(requests = 5, windowSeconds = 60)
  @SensitiveLogData
  public ResponseEntity<ApiResponse<String>> signup(@Valid @RequestBody SelfSignupRequest request) {

    log.info("Public signup requested");

    signupRequests.request(request);

    return ResponseEntity.ok(
        ApiResponse.success(
            "Welcome! Check your email to complete registration.",
            "Registration initiated successfully"));
  }
}
