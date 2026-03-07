package com.fabricmanagement.production.execution.inventory.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Immutable ledger entry for a stock movement against a batch.
 *
 * <p>Every physical inventory change (receive, consume, waste, adjust, transfer) is recorded as a
 * transaction. This provides a full audit trail and enables waste analysis, reconciliation, and
 * event-sourcing-ready architecture.
 *
 * <p>The {@code quantity} field is always positive. Direction is implied by the {@link
 * InventoryTransactionType}:
 *
 * <ul>
 *   <li><b>Inbound</b> (RECEIPT, RETURN): increases batch stock
 *   <li><b>Outbound</b> (CONSUMPTION, WASTE, SAMPLE): decreases batch stock
 *   <li><b>Neutral</b> (TRANSFER, ADJUSTMENT): context-dependent
 * </ul>
 */
@Entity
@Table(
    name = "production_execution_inventory_transaction",
    schema = "production",
    indexes = {
      @Index(name = "idx_inv_txn_tenant", columnList = "tenant_id"),
      @Index(name = "idx_inv_txn_batch", columnList = "batch_id"),
      @Index(name = "idx_inv_txn_type", columnList = "transaction_type"),
      @Index(name = "idx_inv_txn_date", columnList = "transaction_date")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction extends BaseEntity {

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false, length = 30)
  private InventoryTransactionType transactionType;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal quantity;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "transaction_date", nullable = false)
  private Instant transactionDate;

  @Column(name = "reference_id")
  private UUID referenceId;

  @Column(name = "reference_type", length = 50)
  private String referenceType;

  @Column(name = "reason", length = 255)
  private String reason;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  public static InventoryTransaction create(
      UUID tenantId,
      UUID batchId,
      InventoryTransactionType type,
      BigDecimal quantity,
      String unit,
      Instant transactionDate,
      UUID referenceId,
      String referenceType,
      String reason,
      String remarks) {

    InventoryTransaction txn = new InventoryTransaction();
    txn.setTenantId(tenantId);
    txn.setBatchId(batchId);
    txn.setTransactionType(type);
    txn.setQuantity(quantity);
    txn.setUnit(unit);
    txn.setTransactionDate(transactionDate != null ? transactionDate : Instant.now());
    txn.setReferenceId(referenceId);
    txn.setReferenceType(referenceType);
    txn.setReason(reason);
    txn.setRemarks(remarks);
    txn.onCreate();

    return txn;
  }

  @Override
  protected String getModuleCode() {
    return "EXEC-IT";
  }
}
