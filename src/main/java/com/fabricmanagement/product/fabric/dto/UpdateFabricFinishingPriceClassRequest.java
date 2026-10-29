package com.fabricmanagement.product.fabric.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateFabricFinishingPriceClassRequest")
public record UpdateFabricFinishingPriceClassRequest(
    @NotBlank
        @Size(max = 100)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Deep navy")
        String name,
    @Size(max = 2000) String description,
    Integer displayOrder,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean active) {}
