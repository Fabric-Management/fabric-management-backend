package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import java.util.List;
import java.util.UUID;
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
 *
 * <p><b>TenantContext safety:</b> Saves and restores TenantContext around step execution to prevent
 * leaking a newly-created tenant's context into subsequent operations on the same thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantOnboardingOrchestrator {

  private final List<OnboardingStep> steps;

  @Transactional
  public TenantOnboardingResponse onboard(OnboardingContext context) {
    log.info(
        "Onboarding started: organization={}, salesLed={}",
        context.getOrganizationName(),
        context.isSalesLed());

    // Save TenantContext before steps (CreateTenantStep will set a new one)
    UUID previousTenantId = TenantContext.getCurrentTenantIdOrNull();
    try {
      for (int i = 0; i < steps.size(); i++) {
        OnboardingStep step = steps.get(i);
        String stepName = step.getClass().getSimpleName();
        try {
          step.execute(context);
          log.debug("Onboarding step {}/{} completed: {}", i + 1, steps.size(), stepName);
        } catch (Exception e) {
          log.error(
              "Onboarding step {}/{} failed: {} - {}",
              i + 1,
              steps.size(),
              stepName,
              e.getMessage());
          throw e;
        }
      }
    } finally {
      // Restore previous TenantContext to prevent leak
      if (previousTenantId != null) {
        TenantContext.setCurrentTenantId(previousTenantId);
      } else {
        TenantContext.clear();
      }
    }

    TenantOnboardingResponse result = context.toResult();
    log.info(
        "Onboarding completed: organizationId={}, tenantId={}",
        result.getOrganizationId(),
        result.getTenantId());
    return result;
  }
}
