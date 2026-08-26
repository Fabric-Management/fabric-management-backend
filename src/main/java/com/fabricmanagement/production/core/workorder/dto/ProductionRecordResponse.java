package com.fabricmanagement.production.core.workorder.dto;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.workorder.domain.ProductionRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductionRecordResponse(
    UUID id,
    UUID workOrderId,
    UUID stockUnitId,
    String barcode,
    UUID batchId,
    String batchCode,
    ProductType productType,
    BigDecimal outputWeight,
    String unit,
    UUID qualityGradeId,
    Instant producedAt,
    UUID producedBy,
    String notes) {

  public static ProductionRecordResponse from(ProductionRecord entity) {
    if (entity == null) {
      return null;
    }
    return new ProductionRecordResponse(
        entity.getId(),
        entity.getWorkOrderId(),
        entity.getStockUnitId(),
        entity.getBarcode(),
        entity.getBatchId(),
        entity.getBatchCode(),
        entity.getProductType(),
        entity.getOutputWeight(),
        entity.getUnit(),
        entity.getQualityGradeId(),
        entity.getProducedAt(),
        entity.getProducedBy(),
        entity.getNotes());
  }

  public static List<ProductionRecordResponse> from(List<ProductionRecord> entities) {
    if (entities == null) {
      return List.of();
    }
    return entities.stream().map(ProductionRecordResponse::from).toList();
  }
}
