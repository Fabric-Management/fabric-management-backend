package com.fabricmanagement.platform.user.domain;

/** Stable wire IDs in the required evaluation order; never infer them from UI labels. */
@io.swagger.v3.oas.annotations.media.Schema(name = "OnboardingStepId", enumAsRef = true)
public enum OnboardingStepId {
  ORGANIZATION_DETAILS,
  ADDRESS,
  YOUR_DETAILS,
  ORGANIZATION_CONTACTS,
  PLATFORM_MODULES,
  REVIEW
}
