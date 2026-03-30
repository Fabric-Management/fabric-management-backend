package com.fabricmanagement.production.execution.batch.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.common.exception.InsufficientStockException;
import com.fabricmanagement.production.common.exception.InvalidStatusTransitionException;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Universal physical batch entity for all material types (Fiber, Yarn, Fabric, etc.).
 *
 * <p>Replaces the previous module-specific batch tables (e.g. FiberBatch). All physical lots are
 * stored in {@code production_execution_batch} with {@code material_id} and {@code material_type};
 * material-specific properties live in the JSONB {@code attributes} column.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>Links to Material (masterdata) via materialId; materialType indicates the kind of lot
 *   <li>Unique batchCode per tenant for traceability
 *   <li>Tracks quantity, reserved/consumed/waste, and status (AVAILABLE, RESERVED, IN_PROGRESS,
 *       etc.)
 *   <li>Flexible attributes (JSONB) for module-specific fields (e.g. fiber_micronaire, yarn_count)
 * </ul>
 */
@Entity
@Table(name = "production_execution_batch", schema = "production")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Batch extends BaseEntity {

  @Column(name = "material_id", nullable = false)
  private UUID materialId;

  @Enumerated(EnumType.STRING)
  @Column(name = "material_type", nullable = false)
  private MaterialType materialType;

  @org.hibernate.annotations.Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
  @Column(name = "attributes", columnDefinition = "jsonb")
  @Builder.Default
  private java.util.Map<String, Object> attributes = new java.util.HashMap<>();

  @Column(name = "batch_code", nullable = false)
  private String batchCode;

  @Column(name = "supplier_batch_code")
  private String supplierBatchCode;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal quantity;

  @Column(name = "reserved_quantity", nullable = false, precision = 15, scale = 3)
  @Builder.Default
  private BigDecimal reservedQuantity = BigDecimal.ZERO;

  @Column(name = "consumed_quantity", nullable = false, precision = 15, scale = 3)
  @Builder.Default
  private BigDecimal consumedQuantity = BigDecimal.ZERO;

  @Column(name = "waste_quantity", nullable = false, precision = 15, scale = 3)
  @Builder.Default
  private BigDecimal wasteQuantity = BigDecimal.ZERO;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "production_date")
  private Instant productionDate;

  @Column(name = "expiry_date")
  private Instant expiryDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private BatchStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type")
  private BatchSourceType sourceType;

  @Column(name = "source_id")
  private UUID sourceId;

  @Column(name = "location_id")
  private UUID locationId;

  @Column(name = "parent_batch_id")
  private UUID parentBatchId;

  /**
   * Optional FiberQualityStandard for QC. When null, default profile for batch's ISO code is used.
   */
  @Column(name = "quality_standard_id")
  private UUID qualityStandardId;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  @PrePersist
  private void defaultQuantityColumnsIfNull() {
    if (reservedQuantity == null) {
      reservedQuantity = BigDecimal.ZERO;
    }
    if (consumedQuantity == null) {
      consumedQuantity = BigDecimal.ZERO;
    }
    if (wasteQuantity == null) {
      wasteQuantity = BigDecimal.ZERO;
    }
  }

  /**
   * Create a new batch from a {@link CreateBatchCommand}.
   *
   * <p>Preferred factory method — replaces the 13-parameter version. Input validation is performed
   * in the {@code CreateBatchCommand} compact constructor before this method is called.
   */
  public static Batch create(CreateBatchCommand cmd) {
    Batch batch = new Batch();
    batch.setTenantId(cmd.tenantId());
    batch.setMaterialId(cmd.materialId());
    batch.setMaterialType(cmd.materialType());
    batch.setBatchCode(cmd.batchCode());
    batch.setSupplierBatchCode(cmd.supplierBatchCode());
    batch.setQuantity(cmd.quantity());
    batch.setReservedQuantity(BigDecimal.ZERO);
    batch.setConsumedQuantity(BigDecimal.ZERO);
    batch.setWasteQuantity(BigDecimal.ZERO);
    batch.setUnit(cmd.unit());
    batch.setProductionDate(cmd.productionDate());
    batch.setExpiryDate(cmd.expiryDate());
    batch.setStatus(BatchStatus.PENDING_QC);
    batch.setLocationId(cmd.locationId());
    batch.setQualityStandardId(cmd.qualityStandardId());
    batch.setRemarks(cmd.remarks());
    batch.setSourceType(cmd.sourceType());
    batch.setSourceId(cmd.sourceId());
    batch.setAttributes(
        cmd.attributes() != null
            ? new java.util.HashMap<>(cmd.attributes())
            : new java.util.HashMap<>());
    batch.onCreate();
    return batch;
  }

  /**
   * @deprecated Use {@link #create(CreateBatchCommand)} instead. This overload is kept for backward
   *     compatibility during migration and will be removed.
   */
  @Deprecated(forRemoval = true)
  public static Batch create(
      UUID tenantId,
      UUID materialId,
      MaterialType materialType,
      String batchCode,
      String supplierBatchCode,
      BigDecimal quantity,
      String unit,
      Instant productionDate,
      Instant expiryDate,
      UUID locationId,
      UUID qualityStandardId,
      String remarks,
      java.util.Map<String, Object> attributes) {
    return create(
        new CreateBatchCommand(
            tenantId,
            materialId,
            materialType,
            batchCode,
            supplierBatchCode,
            quantity,
            unit,
            productionDate,
            expiryDate,
            locationId,
            qualityStandardId,
            remarks,
            attributes,
            null,
            null));
  }

  /**
   * Get available quantity (quantity - reservedQuantity - consumedQuantity).
   *
   * <p>Note: {@code wasteQuantity} is a subset of {@code consumedQuantity} (you can only waste what
   * was consumed), so it is intentionally NOT subtracted separately here — it is already accounted
   * for within {@code consumedQuantity}.
   */
  public BigDecimal getAvailableQuantity() {
    return quantity.subtract(reservedQuantity).subtract(consumedQuantity);
  }

  /**
   * Transition batch to a target status. Validates allowed transitions per BatchStatus rules.
   *
   * @param target the desired status
   * @param actorId the user performing the transition (for audit; may be stored in future)
   * @throws IllegalStateException if transition from current status to target is not allowed
   */
  public void transitionStatus(BatchStatus target, UUID actorId) {
    if (this.status == target) {
      return;
    }
    if (!this.status.canTransitionTo(target)) {
      throw new InvalidStatusTransitionException("Batch", this.status.name(), target.name());
    }
    this.status = target;
    onUpdate();
  }

  /** Reserve quantity for a production order. */
  public void reserve(BigDecimal qty) {
    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BatchDomainException("Reservation amount must be positive for batch " + batchCode);
    }
    if (BatchStatus.BLOCKED_FOR_PRODUCTION.contains(this.status)) {
      throw new InvalidStatusTransitionException("Batch", this.status.name(), "RESERVED");
    }
    if (this.status == BatchStatus.DEPLETED) {
      throw new InvalidStatusTransitionException("Batch", "DEPLETED", "RESERVED");
    }
    if (getAvailableQuantity().compareTo(qty) < 0) {
      throw new InsufficientStockException(getId(), batchCode, qty, getAvailableQuantity(), unit);
    }
    this.reservedQuantity = this.reservedQuantity.add(qty);
    if (this.status == BatchStatus.AVAILABLE) {
      this.status = BatchStatus.RESERVED;
    }
    onUpdate();
  }

  /** Release reserved quantity (production order cancellation). */
  public void release(BigDecimal qty) {
    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BatchDomainException("Release amount must be positive for batch " + batchCode);
    }
    if (this.reservedQuantity.compareTo(qty) < 0) {
      throw new BatchDomainException(
          String.format(
              "Cannot release %.2f %s from batch %s: only %.2f %s is reserved.",
              qty, unit, batchCode, reservedQuantity, unit));
    }
    this.reservedQuantity = this.reservedQuantity.subtract(qty);
    if (this.reservedQuantity.compareTo(BigDecimal.ZERO) == 0
        && this.status == BatchStatus.RESERVED) {
      this.status = BatchStatus.AVAILABLE;
    }
    onUpdate();
  }

  /** Mark batch as actively in use on the production floor. */
  public void markInUse() {
    if (this.status != BatchStatus.AVAILABLE && this.status != BatchStatus.RESERVED) {
      throw new InvalidStatusTransitionException("Batch", this.status.name(), "IN_PROGRESS");
    }
    this.status = BatchStatus.IN_PROGRESS;
    onUpdate();
  }

  /** Mark batch as fully depleted (terminal state). */
  public void markDepleted() {
    if (this.status != BatchStatus.IN_PROGRESS) {
      throw new InvalidStatusTransitionException("Batch", this.status.name(), "DEPLETED");
    }
    this.status = BatchStatus.DEPLETED;
    onUpdate();
  }

  /**
   * Consume quantity against a named reservation. Decreases {@code reservedQuantity} (the
   * denormalized counter) and increases {@code consumedQuantity}. The caller (service layer) is
   * responsible for also updating the {@link BatchReservation} entity.
   *
   * @param qty amount to consume from reserved stock
   */
  public void consumeFromReservation(BigDecimal qty) {
    validateConsumptionPreconditions(qty);

    if (this.reservedQuantity.compareTo(qty) < 0) {
      throw new BatchDomainException(
          String.format(
              "Cannot consume %.3f %s from reservations of batch %s: only %.3f %s is reserved.",
              qty, unit, batchCode, reservedQuantity, unit));
    }

    this.reservedQuantity = this.reservedQuantity.subtract(qty);
    this.consumedQuantity = this.consumedQuantity.add(qty);
    transitionAfterConsumption();
    onUpdate();
  }

  /**
   * Consume quantity from unreserved (available) stock only. Does NOT touch {@code
   * reservedQuantity}, protecting other work orders' reservations.
   *
   * @param qty amount to consume from available (unreserved) stock
   */
  public void consumeFromAvailable(BigDecimal qty) {
    validateConsumptionPreconditions(qty);

    if (getAvailableQuantity().compareTo(qty) < 0) {
      throw new InsufficientStockException(getId(), batchCode, qty, getAvailableQuantity(), unit);
    }

    this.consumedQuantity = this.consumedQuantity.add(qty);
    transitionAfterConsumption();
    onUpdate();
  }

  private void validateConsumptionPreconditions(BigDecimal qty) {
    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BatchDomainException("Consumption amount must be positive for batch " + batchCode);
    }
    if (BatchStatus.BLOCKED_FOR_PRODUCTION.contains(this.status)) {
      throw new InvalidStatusTransitionException("Batch", this.status.name(), "IN_PROGRESS");
    }
    if (this.status == BatchStatus.DEPLETED) {
      throw new InvalidStatusTransitionException("Batch", "DEPLETED", "IN_PROGRESS");
    }
  }

  private void transitionAfterConsumption() {
    if (this.consumedQuantity.compareTo(this.quantity) >= 0
        && this.reservedQuantity.compareTo(BigDecimal.ZERO) == 0) {
      this.status = BatchStatus.DEPLETED;
    } else if (this.status == BatchStatus.AVAILABLE || this.status == BatchStatus.RESERVED) {
      this.status = BatchStatus.IN_PROGRESS;
    }
  }

  /**
   * Record production waste (fire/telef) — a subset of already-consumed quantity.
   *
   * <p>Invariant: waste_quantity <= consumed_quantity (you can only waste what was consumed).
   */
  public void recordWaste(BigDecimal qty) {
    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BatchDomainException("Waste amount must be positive for batch " + batchCode);
    }
    BigDecimal newWaste = this.wasteQuantity.add(qty);
    if (newWaste.compareTo(this.consumedQuantity) > 0) {
      throw new BatchDomainException(
          String.format(
              "Waste (%.3f %s) would exceed consumed quantity (%.3f %s) for batch %s",
              newWaste, unit, consumedQuantity, unit, batchCode));
    }
    this.wasteQuantity = newWaste;
    onUpdate();
  }

  /**
   * Adjust the total quantity of this batch (physical count correction, write-off, damage, etc.).
   *
   * <p>Positive delta = found more stock; negative delta = write-off / loss. The new quantity must
   * not fall below the sum of reserved + consumed (committed stock).
   *
   * @param delta the adjustment amount (positive or negative)
   */
  public void adjustQuantity(BigDecimal delta) {
    if (delta.compareTo(BigDecimal.ZERO) == 0) {
      throw new BatchDomainException("Adjustment delta must be non-zero for batch " + batchCode);
    }
    BigDecimal newQuantity = this.quantity.add(delta);
    BigDecimal committed = this.reservedQuantity.add(this.consumedQuantity);

    if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
      throw new BatchDomainException("Adjusted quantity cannot be negative");
    }
    if (newQuantity.compareTo(committed) < 0) {
      throw new BatchDomainException(
          String.format(
              "Adjusted quantity (%.3f %s) cannot be less than committed stock "
                  + "(reserved %.3f + consumed %.3f = %.3f %s) for batch %s",
              newQuantity, unit, reservedQuantity, consumedQuantity, committed, unit, batchCode));
    }

    this.quantity = newQuantity;

    if (getAvailableQuantity().compareTo(BigDecimal.ZERO) == 0
        && this.reservedQuantity.compareTo(BigDecimal.ZERO) == 0) {
      this.status = BatchStatus.DEPLETED;
    } else if (this.status == BatchStatus.DEPLETED) {
      // Eğer daha önce tüketim yapılmışsa IN_PROGRESS, hiç dokunulmamışsa AVAILABLE olmalı
      this.status =
          this.consumedQuantity.compareTo(BigDecimal.ZERO) > 0
              ? BatchStatus.IN_PROGRESS
              : BatchStatus.AVAILABLE;
    }

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

  /** Get module code for UID generation. */
  @Override
  protected String getModuleCode() {
    return "BATCH";
  }
}
