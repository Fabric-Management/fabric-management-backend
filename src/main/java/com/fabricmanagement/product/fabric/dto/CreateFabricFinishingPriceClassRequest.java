package com.fabricmanagement.product.fabric.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateFabricFinishingPriceClassRequest")
public record CreateFabricFinishingPriceClassRequest(
    @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "NAVY_DEEP")
        String code,
    @NotBlank
        @Size(max = 100)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Deep navy")
        String name,
    @Size(max = 2000) String description,
    Integer displayOrder) {}
