package com.fabricmanagement.production.execution.fiber.dto;

import com.fabricmanagement.production.execution.fiber.domain.FiberBatch;
import com.fabricmanagement.production.execution.fiber.domain.FiberBatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * FiberBatch DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberBatchDto {
    
    private UUID id;
    private UUID tenantId;
    private String uid;
    private UUID fiberId;
    private String batchCode;
    private String supplierBatchCode;
    private BigDecimal quantity;
    private BigDecimal reservedQuantity;
    private BigDecimal consumedQuantity;
    private BigDecimal availableQuantity;
    private String unit;
    private Instant productionDate;
    private Instant expiryDate;
    private FiberBatchStatus status;
    private String warehouseLocation;
    private String remarks;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Map entity to DTO.
     */
    public static FiberBatchDto from(FiberBatch entity) {
        return FiberBatchDto.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .uid(entity.getUid())
            .fiberId(entity.getFiberId())
            .batchCode(entity.getBatchCode())
            .supplierBatchCode(entity.getSupplierBatchCode())
            .quantity(entity.getQuantity())
            .reservedQuantity(entity.getReservedQuantity())
            .consumedQuantity(entity.getConsumedQuantity())
            .availableQuantity(entity.getAvailableQuantity())
            .unit(entity.getUnit())
            .productionDate(entity.getProductionDate())
            .expiryDate(entity.getExpiryDate())
            .status(entity.getStatus())
            .warehouseLocation(entity.getWarehouseLocation())
            .remarks(entity.getRemarks())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}

