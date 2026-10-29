package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.user.app.OnboardingJourneyService;
import com.fabricmanagement.platform.user.domain.OnboardingStepId;
import com.fabricmanagement.platform.user.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/common/users/me/onboarding")
@PreAuthorize("isAuthenticated()")
@Tag(
    name = "Onboarding Journey",
    description = "Accepted registration progress; full evaluation journey without purchase")
@ApiResponses({
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Accepted journey or original replay result"),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      description = "Malformed, constraint-invalid, unknown or cross-step JSON fields"),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "401",
      description = "Authentication required"),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "403",
      description = "Current membership, company setup or registrant authority required"),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description =
          "Version/operation conflict with currentVersion, order/review conflict, or unresolved signup linkage"),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "422",
      description = "Invalid typed step or package selection; no writes accepted")
})
public class OnboardingJourneyController {
  private final OnboardingJourneyService journeys;

  @GetMapping("/journey")
  @Operation(
      summary = "Resume accepted onboarding progress",
      description =
          "Returns backend-defined company/personal journey kind, ordered steps and capabilities."
              + " Missing company linkage cannot classify a company administrator as personal;"
              + " unresolved linkage returns ONBOARDING_SIGNUP_LINK_UNRESOLVED (409)."
              + " packages contains catalogue options, actual persisted selection and its default/owner source;"
              + " selectModules is the registrant's current versioned package-change capability."
              + " Company snapshots require organisation setup authority; personal mobile belongs to its registrant.")
  public ApiResponse<OnboardingJourneyResponse> getJourney() {
    return ApiResponse.success(journeys.get());
  }

  @PutMapping("/steps/{stepId}")
  @Operation(
      summary = "Accept one onboarding step atomically",
      description =
          "Expected version and client operation ID are required. Never accepts request identity."
              + " Replaying the same canonical typed command returns its frozen result; different content conflicts."
              + " Earlier edits invalidate review when accepted evidence changes; requiresReconfirmation lists it.")
  public ApiResponse<OnboardingJourneyResponse> saveStep(
      @PathVariable OnboardingStepId stepId,
      @Valid @RequestBody SaveOnboardingStepRequest command) {
    return ApiResponse.success(journeys.save(stepId, command));
  }

  @PostMapping("/journey/complete")
  @Operation(
      summary = "Complete the reviewed accepted journey",
      description =
          "Checks all required accepted steps and the frozen review operation server-side."
              + " Company owners cannot bypass this through legacy completion. No paid subscription is required.")
  public ApiResponse<OnboardingJourneyResponse> completeJourney(
      @Valid @RequestBody CompleteOnboardingJourneyRequest command) {
    return ApiResponse.success(journeys.complete(command));
  }
}
