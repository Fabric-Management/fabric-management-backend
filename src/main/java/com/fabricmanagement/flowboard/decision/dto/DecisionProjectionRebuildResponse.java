package com.fabricmanagement.flowboard.decision.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DecisionProjectionRebuildResponse")
public record DecisionProjectionRebuildResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long scanned,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long inserted,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long updated,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long unchanged,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long orphaned) {}
