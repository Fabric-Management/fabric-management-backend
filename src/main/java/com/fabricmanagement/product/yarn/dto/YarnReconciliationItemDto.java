package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueReason;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillResolutionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "One row in the yarn legacy reconciliation queue")
public record YarnReconciliationItemDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productUid,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID articleId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String articleName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) YarnArticleStatus articleStatus,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) YarnBackfillQueueReason reason,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) YarnBackfillQueueStatus status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        YarnBackfillResolutionAction resolutionAction,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        YarnReconciliationResolvedCandidateDto resolvedCandidate,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int candidateOccurrenceCount) {}
