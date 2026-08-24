package com.fabricmanagement.production.core.workorder.dto;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductionLotResponse(
    UUID id,
    UUID workOrderId,
    String lotCode,
    ProductType productType,
    BigDecimal quantity,
    String unit,
    BatchStatus status,
    UUID locationId,
    Instant createdAt) {

  public static ProductionLotResponse from(Batch batch) {
    if (batch == null) {
      return null;
    }
    return new ProductionLotResponse(
        batch.getId(),
        batch.getSourceId(),
        batch.getBatchCode(),
        batch.getProductType(),
        batch.getQuantity(),
        batch.getUnit(),
        batch.getStatus(),
        batch.getLocationId(),
        batch.getCreatedAt());
  }
}
