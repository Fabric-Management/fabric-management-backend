package com.fabricmanagement.product.yarn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Tenant yarn-article readiness report; visibility only, never enforcement")
public record YarnReadinessDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ready,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long activelyUsedCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long blockerCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long openReconciliationCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        YarnUnlinkedOpenDocumentsDto unlinkedOpenYarnDocuments,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long activeYarnProductCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long activeArticleCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int movementWindowDays,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<YarnReadinessBlockerDto> blockers) {}
