package com.fabricmanagement.production.execution.inventory.dto;

import com.fabricmanagement.production.execution.inventory.domain.InventoryTransaction;
import com.fabricmanagement.production.execution.inventory.domain.InventoryTransactionType;
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
public class InventoryTransactionDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID batchId;
  private InventoryTransactionType transactionType;
  private BigDecimal quantity;
  private String unit;
  private Instant transactionDate;
  private UUID referenceId;
  private String referenceType;
  private String reason;
  private String remarks;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;

  public static InventoryTransactionDto from(InventoryTransaction entity) {
    return InventoryTransactionDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .batchId(entity.getBatchId())
        .transactionType(entity.getTransactionType())
        .quantity(entity.getQuantity())
        .unit(entity.getUnit())
        .transactionDate(entity.getTransactionDate())
        .referenceId(entity.getReferenceId())
        .referenceType(entity.getReferenceType())
        .reason(entity.getReason())
        .remarks(entity.getRemarks())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
