package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Batch DTO. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID materialId;
  private com.fabricmanagement.production.masterdata.material.domain.MaterialType materialType;
  private java.util.Map<String, Object> attributes;
  private String batchCode;
  private String supplierBatchCode;
  private BigDecimal quantity;
  private BigDecimal reservedQuantity;
  private BigDecimal consumedQuantity;
  private BigDecimal wasteQuantity;
  private BigDecimal availableQuantity;
  private BigDecimal netOutputQuantity;
  private BigDecimal wastePercentage;
  private String unit;
  private Instant productionDate;
  private Instant expiryDate;
  private BatchStatus status;
  private UUID locationId;
  private String remarks;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  /** Map entity to DTO. */
  public static BatchDto from(Batch entity) {
    return BatchDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .materialId(entity.getMaterialId())
        .materialType(entity.getMaterialType())
        .attributes(entity.getAttributes())
        .batchCode(entity.getBatchCode())
        .supplierBatchCode(entity.getSupplierBatchCode())
        .quantity(entity.getQuantity())
        .reservedQuantity(entity.getReservedQuantity())
        .consumedQuantity(entity.getConsumedQuantity())
        .wasteQuantity(entity.getWasteQuantity())
        .availableQuantity(entity.getAvailableQuantity())
        .netOutputQuantity(entity.getNetOutputQuantity())
        .wastePercentage(entity.getWastePercentage())
        .unit(entity.getUnit())
        .productionDate(entity.getProductionDate())
        .expiryDate(entity.getExpiryDate())
        .status(entity.getStatus())
        .locationId(entity.getLocationId())
        .remarks(entity.getRemarks())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
