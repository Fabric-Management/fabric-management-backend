package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.reference.FabricConstruction;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricConstructionFamily;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "FabricConstruction", description = "A tenant's fabric construction")
public record FabricConstructionDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "TWILL_3_1") String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3/1 twill") String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer displayOrder,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricFormation formation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricConstructionFamily family,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Provided by FabricOS; renameable, never removed")
        boolean systemDefined,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {

  public static FabricConstructionDto from(FabricConstruction construction) {
    return new FabricConstructionDto(
        construction.getId(),
        construction.getCode(),
        construction.getName(),
        construction.getDescription(),
        construction.getDisplayOrder(),
        construction.getFormation(),
        construction.getFamily(),
        construction.isSystemDefined(),
        construction.isUsable());
  }
}
