package com.fabricmanagement.production.execution.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.common.exception.InsufficientStockException;
import com.fabricmanagement.production.common.exception.InvalidStatusTransitionException;
import com.fabricmanagement.production.execution.fiber.domain.exception.FiberBatchDomainException;
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
 * FiberBatch represents a physical production lot/batch of fiber.
 *
 * <p>A FiberBatch is created when fiber is received from a supplier or produced internally. It
 * tracks the actual inventory of fiber that can be used in production.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Links to Fiber (masterdata) via fiberId
 *   <li>Has a unique batchCode for traceability
 *   <li>Tracks quantity and supplierBatch information
 *   <li>Supports status tracking (AVAILABLE, RESERVED, IN_PROGRESS, DEPLETED)
 * </ul>
 */
@Entity
@Table(name = "production_execution_fiber_batch", schema = "production")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberBatch extends BaseEntity {

  @Column(name = "fiber_id", nullable = false)
  private UUID fiberId;

  @Column(name = "batch_code", nullable = false)
  private String batchCode;

  @Column(name = "supplier_batch_code")
  private String supplierBatchCode;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal quantity;

  @Column(name = "reserved_quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal reservedQuantity;

  @Column(name = "consumed_quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal consumedQuantity;

  @Column(name = "waste_quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal wasteQuantity;

  @Column(name = "unit", nullable = false)
  private String unit;

  @Column(name = "production_date")
  private Instant productionDate;

  @Column(name = "expiry_date")
  private Instant expiryDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private FiberBatchStatus status;

  @Column(name = "warehouse_location")
  private String warehouseLocation;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  /** Create a new fiber batch. */
  public static FiberBatch create(
      UUID tenantId,
      UUID fiberId,
      String batchCode,
      String supplierBatchCode,
      BigDecimal quantity,
      String unit,
      Instant productionDate,
      Instant expiryDate,
      String warehouseLocation,
      String remarks) {

    FiberBatch batch = new FiberBatch();
    batch.setTenantId(tenantId);
    batch.setUid(generateUid(batchCode));
    batch.setFiberId(fiberId);
    batch.setBatchCode(batchCode);
    batch.setSupplierBatchCode(supplierBatchCode);
    batch.setQuantity(quantity);
    batch.setReservedQuantity(BigDecimal.ZERO);
    batch.setConsumedQuantity(BigDecimal.ZERO);
    batch.setWasteQuantity(BigDecimal.ZERO);
    batch.setUnit(unit);
    batch.setProductionDate(productionDate);
    batch.setExpiryDate(expiryDate);
    batch.setStatus(FiberBatchStatus.AVAILABLE);
    batch.setWarehouseLocation(warehouseLocation);
    batch.setRemarks(remarks);
    batch.onCreate();

    return batch;
  }

  /** Get available quantity (quantity - reserved - consumed). */
  public BigDecimal getAvailableQuantity() {
    return quantity.subtract(reservedQuantity).subtract(consumedQuantity);
  }

  /** Reserve quantity for a production order. */
  public void reserve(BigDecimal qty) {
    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Reservation amount must be positive");
    }
    if (this.status == FiberBatchStatus.DEPLETED) {
      throw new InvalidStatusTransitionException("FiberBatch", "DEPLETED", "RESERVED");
    }
    if (getAvailableQuantity().compareTo(qty) < 0) {
      throw new InsufficientStockException(batchCode, qty, getAvailableQuantity(), unit);
    }
    this.reservedQuantity = this.reservedQuantity.add(qty);
    if (this.status == FiberBatchStatus.AVAILABLE) {
      this.status = FiberBatchStatus.RESERVED;
    }
    onUpdate();
  }

  /** Release reserved quantity (production order cancellation). */
  public void release(BigDecimal qty) {
    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Release amount must be positive");
    }
    if (this.reservedQuantity.compareTo(qty) < 0) {
      throw new FiberBatchDomainException(
          String.format(
              "Cannot release %.2f %s from batch %s: only %.2f %s is reserved.",
              qty, unit, batchCode, reservedQuantity, unit));
    }
    this.reservedQuantity = this.reservedQuantity.subtract(qty);
    if (this.reservedQuantity.compareTo(BigDecimal.ZERO) == 0
        && this.status == FiberBatchStatus.RESERVED) {
      this.status = FiberBatchStatus.AVAILABLE;
    }
    onUpdate();
  }

  /** Mark batch as actively in use on the production floor. */
  public void markInUse() {
    if (this.status != FiberBatchStatus.AVAILABLE && this.status != FiberBatchStatus.RESERVED) {
      throw new InvalidStatusTransitionException("FiberBatch", this.status.name(), "IN_PROGRESS");
    }
    this.status = FiberBatchStatus.IN_PROGRESS;
    onUpdate();
  }

  /** Mark batch as fully depleted (terminal state). */
  public void markDepleted() {
    if (this.status != FiberBatchStatus.IN_PROGRESS) {
      throw new InvalidStatusTransitionException("FiberBatch", this.status.name(), "DEPLETED");
    }
    this.status = FiberBatchStatus.DEPLETED;
    onUpdate();
  }

  /** Consume quantity from batch (draws from reserved first, then available). */
  public void consume(BigDecimal qty) {
    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Consumption amount must be positive");
    }
    if (this.status == FiberBatchStatus.DEPLETED) {
      throw new InvalidStatusTransitionException("FiberBatch", "DEPLETED", "IN_PROGRESS");
    }

    // Draw from reserved first, then from free available quantity
    BigDecimal fromReserve = reservedQuantity.min(qty);
    this.reservedQuantity = this.reservedQuantity.subtract(fromReserve);
    BigDecimal remaining = qty.subtract(fromReserve);

    if (getAvailableQuantity().compareTo(remaining) < 0) {
      // Restore reserved before throwing so the entity stays consistent
      this.reservedQuantity = this.reservedQuantity.add(fromReserve);
      throw new InsufficientStockException(
          batchCode, qty, getAvailableQuantity().add(fromReserve), unit);
    }

    this.consumedQuantity = this.consumedQuantity.add(qty);

    if (this.consumedQuantity.compareTo(this.quantity) == 0) {
      this.status = FiberBatchStatus.DEPLETED;
    } else if (this.status == FiberBatchStatus.AVAILABLE
        || this.status == FiberBatchStatus.RESERVED) {
      this.status = FiberBatchStatus.IN_PROGRESS;
    }

    onUpdate();
  }

  /**
   * Record production waste (fire/telef) — a subset of already-consumed quantity.
   *
   * <p>Invariant: waste_quantity <= consumed_quantity (you can only waste what was consumed).
   */
  public void recordWaste(BigDecimal qty) {
    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Waste amount must be positive");
    }
    BigDecimal newWaste = this.wasteQuantity.add(qty);
    if (newWaste.compareTo(this.consumedQuantity) > 0) {
      throw new FiberBatchDomainException(
          String.format(
              "Waste (%.3f %s) would exceed consumed quantity (%.3f %s) for batch %s",
              newWaste, unit, consumedQuantity, unit, batchCode));
    }
    this.wasteQuantity = newWaste;
    onUpdate();
  }

  /** Net useful output = consumed - waste. */
  public BigDecimal getNetOutputQuantity() {
    return consumedQuantity.subtract(wasteQuantity);
  }

  /** Waste percentage relative to consumed quantity. Returns 0 if nothing consumed yet. */
  public BigDecimal getWastePercentage() {
    if (consumedQuantity.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return wasteQuantity
        .multiply(new BigDecimal("100"))
        .divide(consumedQuantity, 2, java.math.RoundingMode.HALF_UP);
  }

  private static String generateUid(String batchCode) {
    return "FIBER-BATCH-" + batchCode;
  }

  /** Get module code for UID generation. */
  @Override
  protected String getModuleCode() {
    return "EXEC-FB";
  }
}
