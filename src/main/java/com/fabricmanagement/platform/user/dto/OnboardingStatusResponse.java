package com.fabricmanagement.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for onboarding status check. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Current user's onboarding completion and fresh company setup authority")
public class OnboardingStatusResponse {

  private Boolean hasCompletedOnboarding;
  private Instant completedAt;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description = "Backend authority to configure this tenant's company during onboarding")
  private OnboardingCompanyCapability companySetup;
}
