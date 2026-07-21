package com.fabricmanagement.production.execution.stockunit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(
    name = "StockUnitQcRelocateRequest",
    description = "Privileged custody relocation of unreleased stock into a QC storage area")
public record QcRelocateRequest(
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID targetLocationId,
    @NotBlank @Size(max = 500) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reason) {}
