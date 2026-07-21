package com.fabricmanagement.production.quality.decision.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.time.Instant;
import java.util.UUID;

public record QualityQueueItemDto(
    UUID batchId,
    String batchCode,
    UUID productId,
    ProductType productType,
    String supplierBatchCode,
    long pendingUnitCount,
    Instant receivedAt) {}
