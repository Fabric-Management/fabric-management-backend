package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.app.port.YarnUsageSignal;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Actively-used YARN product whose article is not ACTIVE")
public record YarnReadinessBlockerDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productUid,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID articleId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        YarnArticleStatus articleStatus,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<YarnUsageSignal> signals) {}
