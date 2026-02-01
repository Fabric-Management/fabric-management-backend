package com.fabricmanagement.common.platform.company.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of creating initial OS subscriptions during tenant onboarding.
 *
 * <p>Used by Auth module (TenantOnboardingOrchestrator) via CompanyFacade.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInitialSubscriptionsResult {

  private List<String> osCodes;
  private Instant trialEndsAt;
}
