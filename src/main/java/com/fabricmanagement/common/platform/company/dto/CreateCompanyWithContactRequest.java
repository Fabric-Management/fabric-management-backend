package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.util.validation.LandlineNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCompanyWithContactRequest {

  @NotBlank(message = "Company name is required")
  private String companyName;

  @NotBlank(message = "Tax ID is required")
  private String taxId;

  @NotNull(message = "Company type is required")
  private CompanyType companyType;

  private UUID parentCompanyId;

  @Email(
      message = "Invalid email format",
      regexp = "^$|^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
  private String email;

  /**
   * Phone number in E.164 format (e.g., +905551234567). If country is provided, validates against
   * country-specific format.
   */
  @LandlineNumber(message = "Invalid landline number format. Example: +44 20 7123 4567")
  private String phoneNumber;

  private String address;

  private String city;

  private String state;

  private String postalCode;

  private String country;
}
