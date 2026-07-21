package com.fabricmanagement.production.quality.decision.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record QualityQueueItemDto(
    @Schema(description = "Batch identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID batchId,
    @Schema(description = "Internal batch or lot code", requiredMode = Schema.RequiredMode.REQUIRED)
        String batchCode,
    @Schema(description = "Product identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID productId,
    @Schema(description = "Canonical product UID", requiredMode = Schema.RequiredMode.REQUIRED)
        String productUid,
    @Schema(description = "Product type", requiredMode = Schema.RequiredMode.REQUIRED)
        ProductType productType,
    @Schema(
            description = "Human-readable product name with the canonical UID as fallback",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String productDisplayName,
    @Schema(description = "Assigned tenant color-card identifier", nullable = true) UUID colorId,
    @Schema(description = "Assigned tenant color-card name", nullable = true) String colorName,
    @Schema(description = "Supplier batch or lot code", nullable = true) String supplierBatchCode,
    @Schema(
            description = "Number of active stock units awaiting inspection",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long pendingUnitCount,
    @Schema(description = "Batch creation timestamp", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant batchCreatedAt) {}
