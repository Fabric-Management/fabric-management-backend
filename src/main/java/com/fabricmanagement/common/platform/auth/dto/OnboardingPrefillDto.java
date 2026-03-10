package com.fabricmanagement.common.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data for pre-filling the onboarding form after register or setup-password.
 *
 * <p>When {@link LoginResponse#getNeedsOnboarding()} is true, frontend can use this to auto-fill
 * onboarding form fields (name, email, company name, address) from signup/register data. Sales-led
 * flow: address comes from Organization primary address. Self-signup: address fields null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OnboardingPrefillDto {

  /** Primary contact value (e.g. email) for the user. */
  @JsonProperty("primaryEmail")
  private String primaryEmail;

  /** Organization name (tenant organization) for display in onboarding form. */
  @JsonProperty("organizationName")
  private String organizationName;

  /** Legal registered name; falls back to organizationName if null/empty. */
  @JsonProperty("legalName")
  private String legalName;

  /** Tax ID (tenant company) for read-only display in onboarding form. */
  @JsonProperty("taxId")
  private String taxId;

  /** Organization type (e.g. VERTICAL_MILL) for read-only display in onboarding form. */
  @JsonProperty("organizationType")
  private String organizationType;

  // ─── Address (sales-led: from Organization primary address; self-signup: null) ───

  @JsonProperty("addressLine1")
  private String addressLine1;

  @JsonProperty("addressLine2")
  private String addressLine2;

  @JsonProperty("city")
  private String city;

  @JsonProperty("state")
  private String state;

  @JsonProperty("district")
  private String district;

  @JsonProperty("postalCode")
  private String postalCode;

  @JsonProperty("country")
  private String country;
}
