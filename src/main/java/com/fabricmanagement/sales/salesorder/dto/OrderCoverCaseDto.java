package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverCaseState;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

@Schema(name = "OrderCoverCase")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderCoverCaseDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long revision,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderCoverCaseState state,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID taskId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, minimum = "0")
        Long taskVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> unresolvedLineIds,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID latestEvidenceId) {}
