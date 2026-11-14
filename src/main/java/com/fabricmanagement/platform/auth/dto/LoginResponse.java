package com.fabricmanagement.platform.auth.dto;

import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

  /** Omitted from JSON when null (tokens are sent via Set-Cookie). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String accessToken;

  /** Omitted from JSON when null (tokens are sent via Set-Cookie). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
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
   * True when a setup link added the company to a login that already existed. The link does not
   * sign the person in (their password and MFA still apply): the client sends them to the normal
   * login. Omitted from JSON otherwise.
   */
  @Schema(
      description =
          "True when a setup link added the company to an existing login; no auth cookies are"
              + " set and the client sends the user to the login page. Omitted otherwise.",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean signInRequired;

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
