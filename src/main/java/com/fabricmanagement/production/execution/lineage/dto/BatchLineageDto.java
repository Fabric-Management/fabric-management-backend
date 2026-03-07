package com.fabricmanagement.production.execution.lineage.dto;

import com.fabricmanagement.production.execution.lineage.domain.BatchLineage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchLineageDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID parentBatchId;
  private UUID childBatchId;
  private BigDecimal consumedQuantity;
  private String unit;
  private BigDecimal consumptionPercentage;
  private Instant consumedAt;
  private String processReference;
  private String remarks;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static BatchLineageDto from(BatchLineage entity) {
    return BatchLineageDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .parentBatchId(entity.getParentBatchId())
        .childBatchId(entity.getChildBatchId())
        .consumedQuantity(entity.getConsumedQuantity())
        .unit(entity.getUnit())
        .consumptionPercentage(entity.getConsumptionPercentage())
        .consumedAt(entity.getConsumedAt())
        .processReference(entity.getProcessReference())
        .remarks(entity.getRemarks())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
