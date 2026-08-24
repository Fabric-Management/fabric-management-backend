package com.fabricmanagement.production.core.output.dto;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.output.domain.ProductionOutputStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProductionOutputDto(
    UUID id,
    UUID tenantId,
    String uid,
    UUID workOrderId,
    String workOrderNumber,
    UUID batchId,
    UUID outputProductId,
    ProductType outputProductType,
    String unit,
    ProductionOutputStatus status,
    int totalItemCount,
    BigDecimal totalNetWeight,
    Instant confirmedAt,
    UUID confirmedByUserId,
    String notes,
    List<ProductionOutputItemDto> items) {}
