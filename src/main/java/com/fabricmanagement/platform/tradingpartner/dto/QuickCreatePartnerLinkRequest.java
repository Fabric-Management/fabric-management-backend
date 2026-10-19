package com.fabricmanagement.platform.tradingpartner.dto;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerLinkRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Finds or creates a company that receives goods for a customer, and links it, in one step.
 *
 * <p>Tenant partners that look like the company are offered first. Unless {@code createAnyway} is
 * true, nothing is written while such matches exist.
 */
@Data
public class QuickCreatePartnerLinkRequest {

  @NotBlank(message = "Company name is required")
  @Size(max = 255, message = "Company name must not exceed 255 characters")
  private String companyName;

  @Size(max = 50, message = "Tax ID must not exceed 50 characters")
  private String taxId;

  @NotBlank(message = "Country is required")
  @Size(min = 3, max = 3, message = "Country must be an ISO 3166-1 alpha-3 code")
  private String country;

  @Size(max = 120, message = "City must not exceed 120 characters")
  private String city;

  @Size(max = 30, message = "Phone must not exceed 30 characters")
  private String phone;

  @NotNull(message = "Link role is required")
  private PartnerLinkRole role;

  @Size(max = 500, message = "Note must not exceed 500 characters")
  private String note;

  @Schema(description = "Create (or reuse by tax id) even though similar partners were offered")
  private Boolean createAnyway = false;
}
