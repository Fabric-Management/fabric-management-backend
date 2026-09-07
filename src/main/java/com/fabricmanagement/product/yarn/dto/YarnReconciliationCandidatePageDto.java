package com.fabricmanagement.product.yarn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One page of grouped reconciliation candidates")
public record YarnReconciliationCandidatePageDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<YarnReconciliationCandidateGroupDto> groups,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalGroups,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size) {}
