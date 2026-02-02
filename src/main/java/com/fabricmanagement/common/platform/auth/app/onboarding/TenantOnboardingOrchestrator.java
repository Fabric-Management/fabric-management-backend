package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates tenant onboarding steps in order. Steps are injected with {@link
 * org.springframework.core.annotation.Order}; each step reads and updates {@link
 * OnboardingContext}.
 *
 * <p>Auth module uses facades (OrganizationFacade, TenantFacade, UserFacade) from this
 * orchestrator; steps delegate to facades and auth-only services (token, email).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantOnboardingOrchestrator {

  private final List<OnboardingStep> steps;

  @Transactional
  public TenantOnboardingResponse onboard(OnboardingContext context) {
    log.info(
        "Onboarding started: company={}, salesLed={}",
        context.getCompanyName(),
        context.isSalesLed());
    for (OnboardingStep step : steps) {
      step.execute(context);
    }
    TenantOnboardingResponse result = context.toResult();
    log.info(
        "Onboarding completed: companyId={}, tenantId={}",
        result.getCompanyId(),
        result.getTenantId());
    return result;
  }
}
