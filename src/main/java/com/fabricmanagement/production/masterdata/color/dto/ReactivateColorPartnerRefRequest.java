package com.fabricmanagement.production.masterdata.color.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.util.UUID;

@Schema(
    description =
        "Reactivates a relationship with exactly one existing inactive code or one new primary code",
    oneOf = {
      ReactivateColorPartnerRefWithExistingCodeRequest.class,
      ReactivateColorPartnerRefWithNewCodeRequest.class
    })
public record ReactivateColorPartnerRefRequest(
    @Schema(
            description = "Inactive retained code to reactivate as the sole primary",
            nullable = true)
        UUID existingCodeId,
    @Schema(description = "New code to create as the sole primary", nullable = true) @Valid
        ColorPartnerCodeInput newPrimaryCode) {

  @AssertTrue(message = "exactly one of existingCodeId or newPrimaryCode is required")
  @Schema(hidden = true)
  public boolean isExactlyOneAlternativePresent() {
    return (existingCodeId == null) != (newPrimaryCode == null);
  }
}
