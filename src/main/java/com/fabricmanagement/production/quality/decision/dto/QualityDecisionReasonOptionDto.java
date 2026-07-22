package com.fabricmanagement.production.quality.decision.dto;

import com.fabricmanagement.production.quality.decision.domain.ManualQualityReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;

public record QualityDecisionReasonOptionDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ManualQualityReasonCode code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean remarksRequired) {}
