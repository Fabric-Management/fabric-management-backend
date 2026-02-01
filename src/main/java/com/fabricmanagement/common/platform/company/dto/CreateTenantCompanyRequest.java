package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.CompanyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to create a root tenant company (tenant_id = company_id).
 *
 * <p>Used by Auth module during tenant onboarding. Only company module should create tenant
 * companies via {@link
 * com.fabricmanagement.common.platform.company.api.facade.CompanyFacade#createTenantCompany}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantCompanyRequest {

  @NotBlank(message = "Company name is required")
  private String companyName;

  @NotBlank(message = "Tax ID is required")
  private String taxId;

  @NotNull(message = "Company type is required")
  private CompanyType companyType;
}
