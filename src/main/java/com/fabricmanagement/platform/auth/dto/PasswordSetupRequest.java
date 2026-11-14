package com.fabricmanagement.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uses an emailed setup link: a self-service signup link (builds the company), or a sales-led or
 * invitation link (sets up the user).
 *
 * <p><b>Flow:</b> token + password (the link click verifies the e-mail); no password when the
 * address already has a login. No verification code is needed here; codes are only used for
 * unverified contacts during login flows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordSetupRequest {

  @Schema(
      description = "The token from the setup link",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "Token is required")
  private String token;

  /**
   * Required when the address has no login yet (the service answers {@code AUTH_PASSWORD_REQUIRED}
   * otherwise). Not needed, and ignored, when the address already has a login: the existing
   * password is kept.
   */
  @Schema(
      description =
          "New password (min. 8 characters). Required when the address has no login yet;"
              + " omit it when the link check said passwordRequired = false.",
      minLength = 8,
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @Size(min = 8, message = "Password must be at least 8 characters")
  private String password;
}
