package com.fabricmanagement.production.quality.decision.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record QualityDecisionOptionsDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<QualityDecisionOutcomeOptionDto> options) {}
