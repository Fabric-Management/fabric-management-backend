package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.vocabulary.FabricConstructionFamily;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateFabricConstructionRequest")
public record CreateFabricConstructionRequest(
    @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "TWILL_3_1_S")
        String code,
    @NotBlank
        @Size(max = 100)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3/1 S twill")
        String name,
    @Size(max = 2000) String description,
    Integer displayOrder,
    @NotNull
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The closed family; the formation follows from it")
        FabricConstructionFamily family) {}
