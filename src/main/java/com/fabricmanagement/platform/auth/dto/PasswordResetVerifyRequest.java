package com.fabricmanagement.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Completes a password reset with the code sent to the email address.
 *
 * <p>A wrong code and an address without an account give the same error, so this request cannot be
 * used to find out which addresses are registered either.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetVerifyRequest {

  /** Email address the reset was requested for. */
  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  @Size(max = 320, message = "Email is too long")
  private String contactValue;

  /** Six-digit code from the reset email. */
  @NotBlank(message = "Verification code is required")
  @Pattern(regexp = "^\\d{6}$", message = "Verification code must be 6 digits")
  private String code;

  /** New password; must differ from the current one. */
  @NotBlank(message = "New password is required")
  @Size(min = 8, message = "Password must be at least 8 characters long")
  private String newPassword;
}
