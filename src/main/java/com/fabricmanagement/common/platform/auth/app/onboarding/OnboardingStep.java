package com.fabricmanagement.common.platform.auth.app.onboarding;

/**
 * Single step in the tenant onboarding pipeline. Steps run in order (see {@link
 * org.springframework.core.annotation.Order}); each step can read and update {@link
 * OnboardingContext}.
 */
public interface OnboardingStep {

  void execute(OnboardingContext context);
}
