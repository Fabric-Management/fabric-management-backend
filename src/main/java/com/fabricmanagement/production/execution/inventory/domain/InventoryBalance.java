package com.fabricmanagement.production.execution.inventory.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "production_execution_inventory_balance", schema = "production")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryBalance extends BaseEntity {

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Column(name = "location_id")
  private UUID locationId;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal quantity;

  @Column(name = "reserved_quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal reservedQuantity;

  @Column(name = "consumed_quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal consumedQuantity;

  @Column(name = "waste_quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal wasteQuantity;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "last_transaction_id")
  private UUID lastTransactionId;

  @Column(name = "last_transaction_date")
  private Instant lastTransactionDate;

  public static InventoryBalance create(UUID tenantId, UUID batchId, UUID locationId, String unit) {

    InventoryBalance balance = new InventoryBalance();
    balance.setTenantId(tenantId);
    balance.setBatchId(batchId);
    balance.setLocationId(locationId);
    balance.setQuantity(BigDecimal.ZERO);
    balance.setReservedQuantity(BigDecimal.ZERO);
    balance.setConsumedQuantity(BigDecimal.ZERO);
    balance.setWasteQuantity(BigDecimal.ZERO);
    balance.setUnit(unit);
    balance.onCreate();

    return balance;
  }

  @Override
  protected String getModuleCode() {
    return "IWM-BAL";
  }
}
