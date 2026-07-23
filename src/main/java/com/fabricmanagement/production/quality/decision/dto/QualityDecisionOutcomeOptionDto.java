package com.fabricmanagement.production.quality.decision.dto;

import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record QualityDecisionOutcomeOptionDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QualityDecisionOutcome outcome,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean reasonRequired,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<QualityDecisionReasonOptionDto> reasons) {}
