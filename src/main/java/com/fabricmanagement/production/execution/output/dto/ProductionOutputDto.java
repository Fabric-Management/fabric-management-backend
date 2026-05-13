package com.fabricmanagement.production.execution.output.dto;

import com.fabricmanagement.production.execution.output.domain.ProductionOutputStatus;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
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
