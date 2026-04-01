package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderConsumption;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkOrderConsumptionResponse(
    UUID id,
    UUID workOrderId,
    UUID stockUnitId,
    String barcode,
    UUID batchId,
    String batchCode,
    MaterialType materialType,
    BigDecimal consumedWeight,
    String unit,
    UUID qualityGradeId,
    Instant consumedAt,
    UUID consumedBy) {

  public static WorkOrderConsumptionResponse from(WorkOrderConsumption entity) {
    if (entity == null) {
      return null;
    }
    return new WorkOrderConsumptionResponse(
        entity.getId(),
        entity.getWorkOrderId(),
        entity.getStockUnitId(),
        entity.getBarcode(),
        entity.getBatchId(),
        entity.getBatchCode(),
        entity.getMaterialType(),
        entity.getConsumedWeight(),
        entity.getUnit(),
        entity.getQualityGradeId(),
        entity.getConsumedAt(),
        entity.getConsumedBy());
  }

  public static List<WorkOrderConsumptionResponse> from(List<WorkOrderConsumption> entities) {
    if (entities == null) {
      return List.of();
    }
    return entities.stream().map(WorkOrderConsumptionResponse::from).toList();
  }
}
