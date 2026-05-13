package com.fabricmanagement.production.execution.lineage.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Tracks parent→child batch relationships for full production traceability.
 *
 * <p>Each record represents a specific raw-product lot (parent) consumed to produce an output batch
 * (child). This enables "one step back, one step forward" lineage as required by ISO 22005 and EU
 * Textile Regulation 1007/2011.
 *
 * <p>Example — Blend Batch "BLEND-001" (40% Linen + 60% Wool):
 *
 * <pre>
 *   parent=LINEN-LOT-42  → child=BLEND-001, consumed=400 kg, pct=40%
 *   parent=WOOL-LOT-17   → child=BLEND-001, consumed=600 kg, pct=60%
 * </pre>
 */
@Entity
@Table(name = "production_execution_batch_lineage", schema = "production")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchLineage extends BaseEntity {

  @Column(name = "parent_batch_id", nullable = false)
  private UUID parentBatchId;

  @Column(name = "child_batch_id", nullable = false)
  private UUID childBatchId;

  @Column(name = "consumed_quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal consumedQuantity;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "consumption_percentage", precision = 5, scale = 2)
  private BigDecimal consumptionPercentage;

  @Column(name = "consumed_at", nullable = false)
  private Instant consumedAt;

  @Column(name = "process_reference", length = 255)
  private String processReference;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  public static BatchLineage create(
      UUID tenantId,
      UUID parentBatchId,
      UUID childBatchId,
      BigDecimal consumedQuantity,
      String unit,
      BigDecimal consumptionPercentage,
      Instant consumedAt,
      String processReference,
      String remarks) {

    BatchLineage lineage = new BatchLineage();
    lineage.setTenantId(tenantId);
    lineage.setParentBatchId(parentBatchId);
    lineage.setChildBatchId(childBatchId);
    lineage.setConsumedQuantity(consumedQuantity);
    lineage.setUnit(unit);
    lineage.setConsumptionPercentage(consumptionPercentage);
    lineage.setConsumedAt(consumedAt != null ? consumedAt : Instant.now());
    lineage.setProcessReference(processReference);
    lineage.setRemarks(remarks);
    lineage.onCreate();

    return lineage;
  }

  @Override
  protected String getModuleCode() {
    return "EXEC-BL";
  }
}
