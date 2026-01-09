package com.fabricmanagement.common.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Password reset request.
 *
 * <p>Contains the authUserId from masked contact selection and the contact type (EMAIL or PHONE)
 * for verification.
 *
 * <p><b>Performance Optimization:</b>
 *
 * <p>Uses authUserId for direct lookup instead of masked contact matching, significantly improving
 * performance and security.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {

  /**
   * AuthUser ID from masked contact selection. Used for direct lookup - more secure and performant
   * than masked value matching.
   */
  @NotNull(message = "Auth user ID is required")
  private UUID authUserId;

  /** Contact type: "EMAIL" or "PHONE" Used for validation and user-friendly messaging. */
  @NotBlank(message = "Contact type is required")
  @Pattern(regexp = "EMAIL|PHONE", message = "Contact type must be EMAIL or PHONE")
  private String contactType;
}
