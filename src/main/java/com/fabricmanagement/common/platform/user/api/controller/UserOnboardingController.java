package com.fabricmanagement.common.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.user.app.UserService;
import com.fabricmanagement.common.platform.user.dto.OnboardingStatusResponse;
import com.fabricmanagement.common.platform.user.dto.UserDto;
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
@RequestMapping("/api/common/users/me")
@RequiredArgsConstructor
@Slf4j
public class UserOnboardingController {

  private final UserService userService;

  /** Get onboarding status for current user. */
  @GetMapping("/onboarding-status")
  public ResponseEntity<ApiResponse<OnboardingStatusResponse>> getOnboardingStatus() {
    UUID userId = TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new IllegalStateException("User not authenticated");
    }

    log.debug("Getting onboarding status: userId={}", userId);

    boolean completed = userService.hasCompletedOnboarding(userId);

    return ResponseEntity.ok(
        ApiResponse.success(
            OnboardingStatusResponse.builder().hasCompletedOnboarding(completed).build()));
  }

  /** Complete onboarding for current user. */
  @PostMapping("/onboarding/complete")
  public ResponseEntity<ApiResponse<UserDto>> completeOnboarding() {
    UUID userId = TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new IllegalStateException("User not authenticated");
    }

    log.info("Completing onboarding: userId={}", userId);

    UserDto user = userService.completeOnboarding(userId);

    return ResponseEntity.ok(ApiResponse.success(user, "Onboarding completed successfully"));
  }
}
