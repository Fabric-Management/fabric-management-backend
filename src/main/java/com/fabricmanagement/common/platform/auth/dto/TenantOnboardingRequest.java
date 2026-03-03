package com.fabricmanagement.common.platform.auth.dto;

import com.fabricmanagement.common.platform.organization.domain.OrganizationType;
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
 * <p><b>Critical:</b> Creates company with tenant_id = company_id
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOnboardingRequest {

  @NotBlank(message = "Company name is required")
  private String companyName;

  @NotBlank(message = "Tax ID is required")
  private String taxId;

  @NotNull(message = "Company type is required")
  private OrganizationType companyType;

  private String address;

  private String city;

  private String state;

  private String district;

  private String postalCode;

  private String country;

  private String phoneNumber;

  @Email(message = "Invalid company email")
  private String companyEmail;

  @NotBlank(message = "Admin first name is required")
  private String adminFirstName;

  @NotBlank(message = "Admin last name is required")
  private String adminLastName;

  @NotBlank(message = "Admin contact is required")
  @Email(message = "Invalid admin email")
  private String adminContact;

  private String adminDepartment;

  private List<String> selectedOS;

  @Builder.Default private Integer trialDays = 90;
}
