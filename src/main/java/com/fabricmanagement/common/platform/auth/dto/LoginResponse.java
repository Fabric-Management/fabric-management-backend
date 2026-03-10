package com.fabricmanagement.common.platform.auth.dto;

import com.fabricmanagement.common.platform.auth.domain.MfaType;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

  private String accessToken;
  private String refreshToken;
  private Long expiresIn;
  private UserDto user;

  @Builder.Default private Boolean mfaRequired = false;

  private String mfaToken;

  private String trustedDeviceToken;

  private MfaType mfaType;

  /**
   * Masked contact value shown to the user after MFA code is dispatched.
   *
   * <p>Example: "j***@gmail.com" or "+90 *** *** ** 42". Lets the frontend display "We sent a code
   * to j***@gmail.com" without revealing the full address. Only present when mfaRequired=true.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String maskedContact;

  /**
   * When true, frontend must show onboarding form. Always set explicitly; never null in API
   * response. Serialized as "needsOnboarding" for frontend contract.
   */
  @JsonProperty("needsOnboarding")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Builder.Default
  private Boolean needsOnboarding = false;

  /**
   * Pre-fill data for onboarding form when needsOnboarding is true. Frontend uses user (firstName,
   * lastName) + onboardingPrefill (primaryEmail, organizationName, organizationType). Serialized as
   * "onboardingPrefill".
   */
  @JsonProperty("onboardingPrefill")
  private OnboardingPrefillDto onboardingPrefill;
}
