package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.CompanyType;
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
public class CreateCompanyRequest {

  @NotBlank(message = "Company name is required")
  private String companyName;

  @NotBlank(message = "Tax ID is required")
  private String taxId;

  @NotNull(message = "Company type is required")
  private CompanyType companyType;

  private UUID parentCompanyId;
}
