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
 * Represents the physical consumption of a StockUnit for a specific WorkOrder.
 *
 * <p>This entity is created when an operator scans a barcode on the shop floor and consumes product
 * from a specific physical unit (bale, bobbin, roll). It links the logical production requirement
 * (WorkOrder) to the physical inventory (StockUnit).
 */
@Entity
@Table(
    name = "work_order_consumption",
    schema = "production",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_wo_consumption_tenant_uid",
          columnNames = {"tenant_id", "uid"})
    },
    indexes = {
      @Index(name = "idx_wo_consumption_tenant", columnList = "tenant_id"),
      @Index(name = "idx_wo_consumption_wo", columnList = "tenant_id, work_order_id"),
      @Index(name = "idx_wo_consumption_su", columnList = "tenant_id, stock_unit_id"),
      @Index(name = "idx_wo_consumption_batch", columnList = "tenant_id, batch_id")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkOrderConsumption extends BaseEntity {

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "stock_unit_id", nullable = false)
  private UUID stockUnitId;

  /** Denormalized from StockUnit for reporting without joins. */
  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  /** Denormalized exact barcode scanned. */
  @Column(name = "barcode", nullable = false, length = 50)
  private String barcode;

  /** Denormalized exact batch code consumed from. */
  @Column(name = "batch_code", nullable = false, length = 100)
  private String batchCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "product_type", nullable = false, length = 30)
  private ProductType productType;

  /** Denormalized from Batch.productId — used by the cost engine for per-product price lookup. */
  @Column(name = "product_id")
  private UUID productId;

  @Column(name = "consumed_weight", nullable = false, precision = 15, scale = 3)
  private BigDecimal consumedWeight;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  /** The quality grade of the stock unit at the exact moment it was consumed (snapshot). */
  @Column(name = "quality_grade_id")
  private UUID qualityGradeId;

  @Column(name = "consumed_at", nullable = false)
  private Instant consumedAt;

  @Column(name = "consumed_by", nullable = false)
  private UUID consumedBy;

  /**
   * Factory method to record a new consumption.
   *
   * @param tenantId The current tenant ID
   * @param workOrderId The WorkOrder being fulfilled
   * @param stockUnitId The exact StockUnit being consumed
   * @param batchId Denormalized batch ID
   * @param barcode Denormalized barcode
   * @param batchCode Denormalized batch code
   * @param productType Product type
   * @param consumedWeight Exact weight consumed (must be positive)
   * @param unit Unit of measure
   * @param qualityGradeId Quality grade snapshot at time of consumption
   * @param consumedBy ID of the user recording the consumption
   * @return The saved WorkOrderConsumption
   */
  public static WorkOrderConsumption record(
      UUID tenantId,
      UUID workOrderId,
      UUID stockUnitId,
      UUID batchId,
      String barcode,
      String batchCode,
      ProductType productType,
      UUID productId,
      BigDecimal consumedWeight,
      String unit,
      UUID qualityGradeId,
      UUID consumedBy) {

    if (consumedWeight == null || consumedWeight.compareTo(BigDecimal.ZERO) <= 0) {
      throw new WorkOrderDomainException("Consumed weight must be greater than zero.");
    }
    if (workOrderId == null || stockUnitId == null || batchId == null) {
      throw new WorkOrderDomainException("workOrderId, stockUnitId, and batchId are required.");
    }
    if (barcode == null || barcode.isBlank() || batchCode == null || batchCode.isBlank()) {
      throw new WorkOrderDomainException("barcode and batchCode are required.");
    }
    if (productType == null || unit == null || unit.isBlank() || consumedBy == null) {
      throw new WorkOrderDomainException("productType, unit, and consumedBy are required.");
    }

    WorkOrderConsumption consumption =
        WorkOrderConsumption.builder()
            .workOrderId(workOrderId)
            .stockUnitId(stockUnitId)
            .batchId(batchId)
            .barcode(barcode)
            .batchCode(batchCode)
            .productType(productType)
            .productId(productId)
            .consumedWeight(consumedWeight)
            .unit(unit)
            .qualityGradeId(qualityGradeId)
            .consumedAt(Instant.now())
            .consumedBy(consumedBy)
            .build();

    consumption.setTenantId(tenantId);
    return consumption;
  }

  @Override
  protected String getModuleCode() {
    return "WOC";
  }
}
