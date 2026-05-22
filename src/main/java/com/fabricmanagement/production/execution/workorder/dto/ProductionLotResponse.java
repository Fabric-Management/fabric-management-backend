package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
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
