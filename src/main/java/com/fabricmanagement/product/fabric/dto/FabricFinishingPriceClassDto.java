package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPriceClass;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
    name = "FabricFinishingPriceClass",
    description = "A tier a subcontractor prices a dyeing by, named by the tenant")
public record FabricFinishingPriceClassDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "EXTRA_DARK") String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Extra dark") String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer displayOrder,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Provided by FabricOS; renameable, never removed")
        boolean systemDefined,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {

  public static FabricFinishingPriceClassDto from(FabricFinishingPriceClass priceClass) {
    return new FabricFinishingPriceClassDto(
        priceClass.getId(),
        priceClass.getCode(),
        priceClass.getName(),
        priceClass.getDescription(),
        priceClass.getDisplayOrder(),
        priceClass.isSystemDefined(),
        priceClass.isUsable());
  }
}
