package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverResultDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

@Schema(name = "DecisionTransitionResult")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DecisionTransitionResult(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderCoverResultDto result,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID taskId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long taskVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TaskStatus taskState,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> remainingLineIds,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean replayed) {}
