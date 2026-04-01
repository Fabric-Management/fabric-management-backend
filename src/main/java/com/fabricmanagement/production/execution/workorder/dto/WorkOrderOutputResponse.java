package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderOutput;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkOrderOutputResponse(
    UUID id,
    UUID workOrderId,
    UUID stockUnitId,
    String barcode,
    UUID batchId,
    String batchCode,
    MaterialType materialType,
    BigDecimal outputWeight,
    String unit,
    UUID qualityGradeId,
    Instant producedAt,
    UUID producedBy,
    String notes) {

  public static WorkOrderOutputResponse from(WorkOrderOutput entity) {
    if (entity == null) {
      return null;
    }
    return new WorkOrderOutputResponse(
        entity.getId(),
        entity.getWorkOrderId(),
        entity.getStockUnitId(),
        entity.getBarcode(),
        entity.getBatchId(),
        entity.getBatchCode(),
        entity.getMaterialType(),
        entity.getOutputWeight(),
        entity.getUnit(),
        entity.getQualityGradeId(),
        entity.getProducedAt(),
        entity.getProducedBy(),
        entity.getNotes());
  }

  public static List<WorkOrderOutputResponse> from(List<WorkOrderOutput> entities) {
    if (entities == null) {
      return List.of();
    }
    return entities.stream().map(WorkOrderOutputResponse::from).toList();
  }
}
