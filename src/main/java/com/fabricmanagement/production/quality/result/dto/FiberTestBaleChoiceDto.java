package com.fabricmanagement.production.quality.result.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "An active stock unit that can be named on a fibre test result")
public record FiberTestBaleChoiceDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Stock unit identifier")
        UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Scannable bale code")
        String code) {}
