package com.fabricmanagement.common.platform.user.dto;

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
public class OnboardingStatusResponse {

  private Boolean hasCompletedOnboarding;
  private Instant completedAt;
}
