package com.fabricmanagement.common.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to create the tenant admin user during onboarding.
 *
 * <p>Used by Auth module (TenantOnboardingOrchestrator). User is created with primary contact,
 * pre-verified, and assigned tenant ADMIN role. Optional department assignment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdminUserRequest {

  @NotNull(message = "Company ID is required")
  private UUID companyId;

  @NotNull(message = "Tenant ID is required")
  private UUID tenantId;

  @NotBlank(message = "First name is required")
  private String firstName;

  @NotBlank(message = "Last name is required")
  private String lastName;

  @NotBlank(message = "Contact value (email) is required")
  private String contactValue;

  /** Optional department name for assignment (must exist in seed data). */
  private String department;
}
