package com.fabricmanagement.platform.organization.dto;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for creating an organization. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationRequest {

  @NotBlank(message = "Organization name is required")
  @Size(min = 2, max = 255, message = "Organization name must be between 2 and 255 characters")
  private String name;

  @NotBlank(message = "Tax ID is required")
  @Size(min = 5, max = 50, message = "Tax ID must be between 5 and 50 characters")
  private String taxId;

  @NotNull(message = "Organization type is required")
  @Builder.Default
  private OrganizationType organizationType = OrganizationType.VERTICAL_MILL;

  /** Parent organization for hierarchy (optional) */
  private UUID parentOrganizationId;

  @Pattern(regexp = "^[A-Za-z]{3}$", message = "Preferred currency must be a 3-letter ISO code")
  private String preferredCurrency;
}
