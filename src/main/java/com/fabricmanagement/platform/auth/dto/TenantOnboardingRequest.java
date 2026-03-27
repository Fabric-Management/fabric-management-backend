package com.fabricmanagement.platform.auth.dto;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tenant onboarding request - Sales-led flow.
 *
 * <p>Used by internal sales team to create new tenant companies.
 *
 * <p><b>Critical:</b> Creates tenant and organization (tenant + organization model).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOnboardingRequest {

  @NotBlank(message = "Organization name is required")
  private String organizationName;

  @NotBlank(message = "Tax ID is required")
  private String taxId;

  @NotNull(message = "Organization type is required")
  private OrganizationType organizationType;

  private String address;

  private String city;

  private String state;

  private String district;

  private String postalCode;

  private String country;

  private String phoneNumber;

  @Email(message = "Invalid organization email")
  private String organizationEmail;

  @NotBlank(message = "Admin first name is required")
  private String adminFirstName;

  @NotBlank(message = "Admin last name is required")
  private String adminLastName;

  @NotBlank(message = "Admin contact is required")
  @Email(message = "Invalid admin email")
  private String adminContact;

  private List<String> selectedOS;

  @Builder.Default private Integer trialDays = 90;
}
