package com.fabricmanagement.platform.auth.dto;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request to create a clean trial organization for the current login identity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateExistingAccountOrganizationRequest")
public class CreateExistingAccountOrganizationRequest {

  @NotBlank(message = "Organization name is required")
  @Size(min = 2, max = 255, message = "Organization name must be between 2 and 255 characters")
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Akkaya Tekstil")
  private String organizationName;

  @Size(max = 50, message = "Tax ID must be at most 50 characters")
  @Schema(nullable = true, example = "1234567890")
  private String taxId;

  @NotNull(message = "Organization type is required")
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "VERTICAL_MILL")
  private OrganizationType organizationType;

  @Schema(nullable = true, example = "[\"FabricOS\"]")
  private List<String> selectedOS;
}
