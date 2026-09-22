package com.fabricmanagement.flowboard.decision.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "DecisionQueueSummary")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DecisionQueueSummary(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long mineCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long departmentCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, minimum = "0")
        Long unassignedCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long waitingCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant computedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean stale) {}
