package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(
    name = "UpdateFabricFinishingProcessRequest",
    description = "Code and category never change; lots reason on them")
public record UpdateFabricFinishingProcessRequest(
    @NotBlank @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Size(max = 2000) String description,
    Integer displayOrder,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FinishingChargeUnit chargeUnit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
