package com.fabricmanagement.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Starts a password reset for an email address.
 *
 * <p>The response is the same whether or not an account uses this address, so the request cannot be
 * used to find out which addresses are registered.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {

  /** Email address the person signs in with. */
  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  @Size(max = 320, message = "Email is too long")
  private String contactValue;
}
