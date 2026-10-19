package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateFabricFinishingProcessRequest")
public record CreateFabricFinishingProcessRequest(
    @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ENZYME_WASH")
        String code,
    @NotBlank
        @Size(max = 100)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Enzyme wash")
        String name,
    @Size(max = 2000) String description,
    Integer displayOrder,
    @NotNull
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The closed category; it never changes")
        FabricFinishingCategory category,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FinishingChargeUnit chargeUnit) {}
