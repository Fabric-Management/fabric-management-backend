package com.fabricmanagement.production.execution.inventory.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionReasonCode;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.execution.inventory.domain.enums.ReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "production_execution_inventory_transaction", schema = "production")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction extends BaseEntity {

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false, length = 50)
  private InventoryTransactionType transactionType;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal quantity;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "location_id")
  private UUID locationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "reference_type", length = 50)
  private ReferenceType referenceType;

  @Column(name = "reference_id")
  private UUID referenceId;

  @Column(name = "transaction_date", nullable = false)
  private Instant transactionDate;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", length = 50)
  private InventoryTransactionReasonCode reasonCode;

  @Column(name = "idempotency_key", length = 255)
  private String idempotencyKey;

  public static InventoryTransaction create(
      UUID tenantId,
      UUID batchId,
      InventoryTransactionType transactionType,
      BigDecimal quantity,
      String unit,
      UUID locationId,
      ReferenceType referenceType,
      UUID referenceId,
      Instant transactionDate,
      String remarks,
      InventoryTransactionReasonCode reasonCode,
      String idempotencyKey) {

    InventoryTransaction transaction = new InventoryTransaction();
    transaction.setTenantId(tenantId);
    transaction.setBatchId(batchId);
    transaction.setTransactionType(transactionType);
    transaction.setQuantity(quantity);
    transaction.setUnit(unit);
    transaction.setLocationId(locationId);
    transaction.setReferenceType(referenceType);
    transaction.setReferenceId(referenceId);
    transaction.setTransactionDate(transactionDate != null ? transactionDate : Instant.now());
    transaction.setRemarks(remarks);
    transaction.setReasonCode(reasonCode);
    transaction.setIdempotencyKey(idempotencyKey);
    transaction.onCreate();

    return transaction;
  }

  @Override
  protected String getModuleCode() {
    return "IWM-TXN";
  }
}
