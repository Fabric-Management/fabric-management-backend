package com.fabricmanagement.production.execution.workorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents a physical StockUnit produced (output) by a specific WorkOrder.
 *
 * <p>This entity links the logical production fulfillment back to the physical inventory generated
 * during the manufacturing process. Each record captures one produced item (bobbin, bale, pallet)
 * along with its quality snapshot at production time.
 *
 * <p>Note: DB table name retained as {@code work_order_output} for migration compatibility.
 */
@Entity
@Table(
    name = "work_order_output",
    schema = "production",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_wo_output_tenant_uid",
          columnNames = {"tenant_id", "uid"}),
      @UniqueConstraint(
          name = "uq_wo_output_tenant_su",
          columnNames = {"tenant_id", "stock_unit_id"})
    },
    indexes = {
      @Index(name = "idx_wo_output_tenant", columnList = "tenant_id"),
      @Index(name = "idx_wo_output_wo", columnList = "tenant_id, work_order_id"),
      @Index(name = "idx_wo_output_su", columnList = "tenant_id, stock_unit_id"),
      @Index(name = "idx_wo_output_batch", columnList = "tenant_id, batch_id")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductionRecord extends BaseEntity {

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "stock_unit_id", nullable = false)
  private UUID stockUnitId;

  /** Denormalized from StockUnit for fast reporting. */
  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  /** Denormalized exact barcode produced. */
  @Column(name = "barcode", nullable = false, length = 50)
  private String barcode;

  /** Denormalized exact batch code produced. */
  @Column(name = "batch_code", nullable = false, length = 100)
  private String batchCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "product_type", nullable = false, length = 30)
  private ProductType productType;

  @Column(name = "output_weight", nullable = false, precision = 15, scale = 3)
  private BigDecimal outputWeight;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  /** The quality grade of the stock unit at the moment it was produced (snapshot). */
  @Column(name = "quality_grade_id")
  private UUID qualityGradeId;

  @Column(name = "produced_at", nullable = false)
  private Instant producedAt;

  @Column(name = "produced_by", nullable = false)
  private UUID producedBy;

  @Column(name = "notes")
  private String notes;

  /** Factory method to record a new production record for a WorkOrder. */
  public static ProductionRecord record(
      UUID tenantId,
      UUID workOrderId,
      UUID stockUnitId,
      UUID batchId,
      String barcode,
      String batchCode,
      ProductType productType,
      BigDecimal outputWeight,
      String unit,
      UUID qualityGradeId,
      UUID producedBy,
      String notes) {

    if (outputWeight == null || outputWeight.compareTo(BigDecimal.ZERO) <= 0) {
      throw new WorkOrderDomainException("Output weight must be greater than zero.");
    }
    if (workOrderId == null || stockUnitId == null || batchId == null) {
      throw new WorkOrderDomainException("workOrderId, stockUnitId, and batchId are required.");
    }
    if (barcode == null || barcode.isBlank() || batchCode == null || batchCode.isBlank()) {
      throw new WorkOrderDomainException("barcode and batchCode are required.");
    }
    if (productType == null || unit == null || unit.isBlank() || producedBy == null) {
      throw new WorkOrderDomainException("productType, unit, and producedBy are required.");
    }

    ProductionRecord record =
        ProductionRecord.builder()
            .workOrderId(workOrderId)
            .stockUnitId(stockUnitId)
            .batchId(batchId)
            .barcode(barcode)
            .batchCode(batchCode)
            .productType(productType)
            .outputWeight(outputWeight)
            .unit(unit)
            .qualityGradeId(qualityGradeId)
            .producedAt(Instant.now())
            .producedBy(producedBy)
            .notes(notes)
            .build();

    record.setTenantId(tenantId);
    return record;
  }

  @Override
  protected String getModuleCode() {
    return "WOO";
  }
}
