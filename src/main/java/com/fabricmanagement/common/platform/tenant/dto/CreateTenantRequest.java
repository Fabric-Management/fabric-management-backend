package com.fabricmanagement.common.platform.tenant.dto;

import com.fabricmanagement.common.platform.tenant.domain.TenantSettings;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new tenant.
 *
 * <p>Used during tenant onboarding flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {

  /** Display name of the tenant/organization */
  @NotBlank(message = "Tenant name is required")
  @Size(min = 2, max = 255, message = "Tenant name must be between 2 and 255 characters")
  private String name;

  /** Primary email for billing and critical notifications */
  @Email(message = "Invalid email format")
  private String billingEmail;

  /** Custom settings (optional - uses defaults if not provided) */
  private TenantSettings settings;

  /** Trial period in days (default: 14) */
  @Builder.Default private int trialDays = 14;

  /** Country code for locale defaults (ISO 3166-1 alpha-2) */
  private String country;
}
