package com.fabricmanagement.product.fabric.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    name = "UpdateFabricConstructionRequest",
    description = "Code, family and formation never change; articles reason on them")
public record UpdateFabricConstructionRequest(
    @NotBlank @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Size(max = 2000) String description,
    Integer displayOrder,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
