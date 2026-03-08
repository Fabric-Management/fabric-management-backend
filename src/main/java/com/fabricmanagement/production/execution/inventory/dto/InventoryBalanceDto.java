package com.fabricmanagement.production.execution.inventory.dto;

import com.fabricmanagement.production.execution.inventory.domain.InventoryBalance;
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
public class InventoryBalanceDto {

  private UUID id;
  private UUID tenantId;
  private UUID batchId;
  private UUID locationId;
  private BigDecimal quantity;
  private BigDecimal reservedQuantity;
  private BigDecimal consumedQuantity;
  private BigDecimal wasteQuantity;
  private String unit;
  private UUID lastTransactionId;
  private Instant lastTransactionDate;
  private Instant updatedAt;

  public static InventoryBalanceDto from(InventoryBalance entity) {
    return InventoryBalanceDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .batchId(entity.getBatchId())
        .locationId(entity.getLocationId())
        .quantity(entity.getQuantity())
        .reservedQuantity(entity.getReservedQuantity())
        .consumedQuantity(entity.getConsumedQuantity())
        .wasteQuantity(entity.getWasteQuantity())
        .unit(entity.getUnit())
        .lastTransactionId(entity.getLastTransactionId())
        .lastTransactionDate(entity.getLastTransactionDate())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
