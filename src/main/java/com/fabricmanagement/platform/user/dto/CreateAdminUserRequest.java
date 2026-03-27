package com.fabricmanagement.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

  @NotNull(message = "Organization ID is required")
  private UUID organizationId;

  @NotNull(message = "Tenant ID is required")
  private UUID tenantId;

  @NotBlank(message = "First name is required")
  @Size(max = 100, message = "First name must be at most 100 characters")
  private String firstName;

  @NotBlank(message = "Last name is required")
  @Size(max = 100, message = "Last name must be at most 100 characters")
  private String lastName;

  @NotBlank(message = "Contact value (email) is required")
  @Size(max = 255, message = "Contact value must be at most 255 characters")
  private String contactValue;
}
