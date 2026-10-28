package com.fabricmanagement.platform.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Catalogue-backed evaluation access. No payment or billing activation is implied.")
public record EvaluationPackageSelection(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String catalogueVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Option> options,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> selectedCodes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean editable,
    @Schema(nullable = true) String reason,
    @Schema(
            nullable = true,
            allowableValues = {"SERVER_DEFAULT", "OWNER", "REPAIRED_DEFAULT", "REPAIRED_PRESERVED"})
        String source) {
  @Schema(name = "EvaluationPackageOption")
  public record Option(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean required) {}
}
