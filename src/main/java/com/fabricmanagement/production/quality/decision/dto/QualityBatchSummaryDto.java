package com.fabricmanagement.production.quality.decision.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record QualityBatchSummaryDto(
    @Schema(description = "Batch identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID batchId,
    @Schema(description = "Internal batch or lot code", requiredMode = Schema.RequiredMode.REQUIRED)
        String batchCode,
    @Schema(description = "Product identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID productId,
    @Schema(description = "Canonical product UID", requiredMode = Schema.RequiredMode.REQUIRED)
        String productUid,
    @Schema(
            description = "Human-readable product name with the canonical UID as fallback",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String productDisplayName,
    @Schema(description = "Product type", requiredMode = Schema.RequiredMode.REQUIRED)
        ProductType productType,
    @Schema(description = "Assigned tenant color-card identifier", nullable = true) UUID colorId,
    @Schema(description = "Assigned tenant color-card name", nullable = true) String colorName,
    @Schema(description = "Batch creation timestamp", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant batchCreatedAt,
    @Schema(
            description = "Active units awaiting inspection",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long pendingInspectionCount,
    @Schema(description = "Active released units", requiredMode = Schema.RequiredMode.REQUIRED)
        long releasedCount,
    @Schema(description = "Active quarantined units", requiredMode = Schema.RequiredMode.REQUIRED)
        long quarantinedCount,
    @Schema(description = "Active nonconforming units", requiredMode = Schema.RequiredMode.REQUIRED)
        long nonconformingCount,
    @Schema(description = "Total active units", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalCount) {}
