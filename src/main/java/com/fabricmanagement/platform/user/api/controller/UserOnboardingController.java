package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.app.UserService;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.dto.OnboardingStatusResponse;
import com.fabricmanagement.platform.user.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Onboarding endpoints for the current user.
 *
 * <p>Base path: /api/common/users/me — onboarding status and completion.
 */
@RestController
@RequestMapping("/api/v1/common/users/me")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Onboarding", description = "User Onboarding operations")
public class UserOnboardingController {

  private final UserService userService;

  /** Get onboarding status for current user. */
  @GetMapping("/onboarding-status")
  @Operation(
      summary = "Get own onboarding status and company setup capability",
      description =
          "Returns fresh company setup authority; capability is rechecked on every write.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Own status and current company setup capability")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "401",
      description = "Authentication required")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "403",
      description = "Active matching tenant membership required")
  public ResponseEntity<ApiResponse<OnboardingStatusResponse>> getOnboardingStatus() {
    UUID userId = TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    log.debug("Getting onboarding status: userId={}", userId);

    return ResponseEntity.ok(ApiResponse.success(userService.getOnboardingStatus(userId)));
  }

  /**
   * Complete onboarding for current user.
   *
   * <p>Accepts an optional {@link CompleteOnboardingRequest} body with company enrichment data
   * (legalName, industry, address, etc.) and the current user's personal mobile. If the body is
   * absent or null, legacy personal callers can still complete. Registered company owners with an
   * incomplete journey receive ONBOARDING_JOURNEY_REQUIRED and must use its versioned commands.
   */
  @PostMapping("/onboarding/complete")
  @Operation(
      summary = "Complete own onboarding",
      description =
          "Legacy personal-only invited users retain self-service completion. Registered company"
              + " owners must accept all six steps and use /onboarding/journey/complete; this legacy"
              + " operation returns ONBOARDING_JOURNEY_REQUIRED until that review is completed."
              + " Previously completed owners may still correct their profile with retained evidence."
              + " Missing company signup linkage returns ONBOARDING_SIGNUP_LINK_UNRESOLVED;"
              + " company changes require a retained company journey. Changed retained values invalidate review."
              + " Any non-null company field, including"
              + " blank values or selectedOS, requires organization:configure at ORGANIZATION or"
              + " GLOBAL scope. An unauthorised mixed request is rejected before any write."
              + " Profile changes and first completion commit atomically; replays reuse resources."
              + " Absent/null fields preserve data. Blank optional profile/address scalars explicitly"
              + " clear them; identity, required address and contact fields cannot be blank.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Onboarding completed")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "401",
      description = "Authentication required")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "403",
      description = "Company setup authority or matching membership required; no writes performed")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description =
          "Unresolved signup linkage, missing root organisation, duplicate tax ID, shared headquarters, or registered owner must use versioned journey completion")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "422",
      description = "Invalid profile or incomplete new headquarters; no changes committed")
  public ResponseEntity<ApiResponse<UserDto>> completeOnboarding(
      @RequestBody(required = false) @Valid CompleteOnboardingRequest request) {
    UUID userId = TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    log.info("Completing onboarding: userId={}", userId);

    UserDto user = userService.completeOnboarding(userId, request);

    return ResponseEntity.ok(ApiResponse.success(user, "Onboarding completed successfully"));
  }
}
