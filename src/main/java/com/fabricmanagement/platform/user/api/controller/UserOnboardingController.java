package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.app.UserService;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.dto.OnboardingStatusResponse;
import com.fabricmanagement.platform.user.dto.UserDto;
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
  public ResponseEntity<ApiResponse<OnboardingStatusResponse>> getOnboardingStatus() {
    UUID userId = TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    log.debug("Getting onboarding status: userId={}", userId);

    boolean completed = userService.hasCompletedOnboarding(userId);

    return ResponseEntity.ok(
        ApiResponse.success(
            OnboardingStatusResponse.builder().hasCompletedOnboarding(completed).build()));
  }

  /**
   * Complete onboarding for current user.
   *
   * <p>Accepts an optional {@link CompleteOnboardingRequest} body with company enrichment data
   * (legalName, industry, address, etc.). If the body is absent or null, onboarding is still marked
   * complete and no enrichment is performed — existing callers remain unaffected.
   */
  @PostMapping("/onboarding/complete")
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
