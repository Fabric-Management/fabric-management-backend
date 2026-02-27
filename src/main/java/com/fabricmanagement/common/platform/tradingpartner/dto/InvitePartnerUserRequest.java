package com.fabricmanagement.common.platform.tradingpartner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for inviting a new user to the partner portal.
 *
 * <p>The {@code partnerRoleCode} must be one of: {@code PARTNER_OWNER}, {@code PARTNER_ACCOUNTANT},
 * {@code PARTNER_BUYER}, {@code PARTNER_VIEWER}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitePartnerUserRequest {

  @NotBlank(message = "First name is required")
  private String firstName;

  @NotBlank(message = "Last name is required")
  private String lastName;

  @NotBlank(message = "Email is required")
  @Email(message = "Must be a valid email address")
  private String email;

  @NotBlank(message = "Partner role code is required")
  private String partnerRoleCode;
}
