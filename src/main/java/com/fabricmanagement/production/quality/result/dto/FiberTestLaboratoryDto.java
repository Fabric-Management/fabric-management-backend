package com.fabricmanagement.production.quality.result.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "FiberTestLaboratoryDto")
public record FiberTestLaboratoryDto(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Trading partner identifier.")
        UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255) String name) {}
