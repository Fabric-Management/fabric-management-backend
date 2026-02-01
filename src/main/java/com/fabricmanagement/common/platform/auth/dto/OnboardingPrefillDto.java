package com.fabricmanagement.common.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data for pre-filling the onboarding form after register or setup-password.
 *
 * <p>When {@link LoginResponse#getNeedsOnboarding()} is true, frontend can use this to auto-fill
 * onboarding form fields (name, email, company name) from signup/register data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingPrefillDto {

  /** Primary contact value (e.g. email) for the user. */
  @JsonProperty("primaryEmail")
  private String primaryEmail;

  /** Company name (tenant company) for display in onboarding form. */
  @JsonProperty("companyName")
  private String companyName;

  /** Tax ID (tenant company) for read-only display in onboarding form. */
  @JsonProperty("taxId")
  private String taxId;

  /** Company type (e.g. VERTICAL_MILL) for read-only display in onboarding form. */
  @JsonProperty("companyType")
  private String companyType;
}
