package com.fabricmanagement.production.execution.stockunit.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.execution.stockunit.domain.exception.InsufficientWeightException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.InvalidPackageTypeException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitDomainException;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single physical inventory unit — one bale, one bobbin, one roll.
 *
 * <p>StockUnit is the finest-grained unit of inventory management. Every physical package that
 * enters the warehouse gets a StockUnit with a scannable barcode. The parent {@code Batch} is the
 * lot-level aggregate; StockUnit is the physical-unit-level detail.
 *
 * <h2>Invariants (enforced in this entity — service layer cannot bypass)</h2>
 *
 * <ul>
 *   <li>{@code initialWeight > 0} — always positive at creation
 *   <li>{@code 0 <= currentWeight <= initialWeight} — no negative weight, no exceeding initial
 *   <li>{@code packageType} must be compatible with {@code materialType}
 *   <li>{@code status = DEPLETED} automatically when {@code currentWeight == 0}
 *   <li>{@code status = PARTIAL} automatically when {@code 0 < currentWeight < initialWeight}
 *   <li>Consumption only allowed in AVAILABLE or PARTIAL status
 *   <li>Reversal only up to consumed amount ({@code initialWeight - currentWeight})
 * </ul>
 *
 * <h2>Batch Relationship</h2>
 *
 * <p>StockUnit belongs to exactly one Batch. The Batch status acts as a <b>gate</b> — when the
 * Batch is ON_HOLD or QUARANTINE, new consumptions are blocked at the service layer. StockUnit
 * statuses are <b>NOT</b> cascaded from Batch; individual units retain their own status.
 *
 * <h2>materialType Denormalization</h2>
 *
 * <p>{@code materialType} is denormalized from the parent Batch to allow entity-level package type
 * validation without a round-trip to the database.
 */
@Entity
@Table(
    name = "stock_unit",
    schema = "production",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_stock_unit_tenant_barcode",
          columnNames = {"tenant_id", "barcode"})
    },
    indexes = {
      @Index(
          name = "idx_stock_unit_tenant_batch_status",
          columnList = "tenant_id, batch_id, status"),
      @Index(
          name = "idx_stock_unit_tenant_location_status",
          columnList = "tenant_id, location_id, status"),
      @Index(name = "idx_stock_unit_tenant_grade", columnList = "tenant_id, quality_grade_id"),
      @Index(name = "idx_stock_unit_barcode", columnList = "barcode"),
      @Index(name = "idx_stock_unit_flagged", columnList = "tenant_id, flagged")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StockUnit extends BaseEntity {

  // ── Identity ──────────────────────────────────────────────────────────────

  /**
   * Scannable barcode. Unique per tenant.
   *
   * <p>For units created from GoodsReceipt, this is copied from {@code GoodsReceiptItem.barcode}.
   * The format is: {@code {SOURCE_PREFIX}-{REFERENCE}-{4_DIGIT_SEQ}-{CHECK_DIGIT}}
   */
  @Column(name = "barcode", nullable = false, length = 50)
  private String barcode;

  /** Optional manufacturer/supplier serial number. Nullable. */
  @Column(name = "serial_number", length = 100)
  private String serialNumber;

  /** FK to the parent Batch (lot-level aggregate). */
  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  // ── Physical Properties ───────────────────────────────────────────────────

  /** Package type (BALE, BOBBIN, ROLL…). Must be compatible with {@code materialType}. */
  @Enumerated(EnumType.STRING)
  @Column(name = "package_type", nullable = false, length = 20)
  private PackageType packageType;

  /**
   * Denormalized material type from the parent Batch.
   *
   * <p>Used exclusively for package type compatibility validation without a join. Not exposed to
   * callers via a setter — set only at creation time.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "material_type", nullable = false, length = 20)
  private MaterialType materialType;

  /**
   * Weight at creation (first weigh-in). Immutable after creation — never changes.
   *
   * <p>Used as the baseline for consumption tracking and audit comparison.
   */
  @Column(name = "initial_weight", nullable = false, precision = 15, scale = 3)
  private BigDecimal initialWeight;

  /**
   * Current remaining weight. Decreases on consumption, may increase on reversal.
   *
   * <p>Invariant: {@code 0 <= currentWeight <= initialWeight}
   */
  @Column(name = "current_weight", nullable = false, precision = 15, scale = 3)
  private BigDecimal currentWeight;

  /** Gross weight including packaging. Nullable. */
  @Column(name = "gross_weight", precision = 15, scale = 3)
  private BigDecimal grossWeight;

  /** Unit of weight measurement — shared with parent Batch. */
  @Column(name = "unit", nullable = false, length = 10)
  private String unit;

  // ── Location ──────────────────────────────────────────────────────────────

  /**
   * Current warehouse location (soft FK to {@code iwm.warehouse_location}).
   *
   * <p>Cross-schema FK enforced at the service layer, not as a database constraint.
   */
  @Column(name = "location_id")
  private UUID locationId;

  /** Previous location ID — populated during transfers for quick audit trail. */
  @Column(name = "previous_location_id")
  private UUID previousLocationId;

  // ── Quality ───────────────────────────────────────────────────────────────

  /**
   * Current quality grade (soft FK to {@code production.quality_grade}).
   *
   * <p>Null until assigned by QC (after goods receipt, before production use).
   */
  @Column(name = "quality_grade_id")
  private UUID qualityGradeId;

  /** Previous grade ID — populated on grade change for audit trail. */
  @Column(name = "previous_grade_id")
  private UUID previousGradeId;

  // ── Status ────────────────────────────────────────────────────────────────

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private StockUnitStatus status;

  // ── Source (Traceability) ─────────────────────────────────────────────────

  /** How this stock unit was created (GOODS_RECEIPT, PRODUCTION, SPLIT, RETURN). */
  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private StockUnitSourceType sourceType;

  /**
   * Polymorphic source reference ID.
   *
   * <ul>
   *   <li>GOODS_RECEIPT → GoodsReceiptItem.id
   *   <li>PRODUCTION → ProductionOutput.id
   *   <li>SPLIT → original StockUnit.id
   *   <li>RETURN → RMA.id
   * </ul>
   */
  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  // ── AI / Anomaly Flags ────────────────────────────────────────────────────

  /** True if an anomaly flag has been raised (e.g. weight anomaly, declaration mismatch). */
  @Column(name = "flagged", nullable = false)
  @Builder.Default
  private boolean flagged = false;

  /** Reason code for the current flag (WEIGHT_ANOMALY, DECLARATION_MISMATCH, etc.). Nullable. */
  @Column(name = "flag_reason", length = 50)
  private String flagReason;

  /** Additional human-readable flag details / AI explanation text. */
  @Column(name = "flag_details", columnDefinition = "TEXT")
  private String flagDetails;

  // ── Factory Method ────────────────────────────────────────────────────────

  /**
   * Creates a new StockUnit with full invariant validation.
   *
   * <p>Validates:
   *
   * <ul>
   *   <li>initialWeight > 0
   *   <li>packageType compatibility with materialType
   *   <li>required IDs are non-null
   * </ul>
   *
   * @param tenantId tenant context
   * @param batchId parent batch
   * @param materialType denormalized from parent batch
   * @param barcode pre-generated scannable barcode
   * @param serialNumber optional supplier serial number
   * @param packageType physical packaging type
   * @param initialWeight gross weigh-in at receipt (immutable after creation)
   * @param grossWeight optional gross weight including packaging
   * @param unit weight unit (KG, MT, PIECE)
   * @param locationId initial warehouse location
   * @param sourceType origin type
   * @param sourceId origin record ID
   * @return new StockUnit in AVAILABLE status
   */
  public static StockUnit create(
      UUID tenantId,
      UUID batchId,
      MaterialType materialType,
      String barcode,
      String serialNumber,
      PackageType packageType,
      BigDecimal initialWeight,
      BigDecimal grossWeight,
      String unit,
      UUID locationId,
      StockUnitSourceType sourceType,
      UUID sourceId) {

    if (batchId == null) {
      throw new StockUnitDomainException("batchId must not be null");
    }
    if (materialType == null) {
      throw new StockUnitDomainException("materialType must not be null");
    }
    if (barcode == null || barcode.isBlank()) {
      throw new StockUnitDomainException("barcode must not be blank");
    }
    if (packageType == null) {
      throw new StockUnitDomainException("packageType must not be null");
    }
    if (initialWeight == null || initialWeight.compareTo(BigDecimal.ZERO) <= 0) {
      throw new StockUnitDomainException("initialWeight must be positive, got: " + initialWeight);
    }
    if (!packageType.isCompatibleWith(materialType)) {
      throw new InvalidPackageTypeException(packageType, materialType);
    }

    StockUnit stockUnit =
        StockUnit.builder()
            .batchId(batchId)
            .materialType(materialType)
            .barcode(barcode.trim())
            .serialNumber(serialNumber)
            .packageType(packageType)
            .initialWeight(initialWeight)
            .currentWeight(initialWeight)
            .grossWeight(grossWeight)
            .unit(unit)
            .locationId(locationId)
            .qualityGradeId(null)
            .previousGradeId(null)
            .previousLocationId(null)
            .status(StockUnitStatus.AVAILABLE)
            .sourceType(sourceType)
            .sourceId(sourceId)
            .flagged(false)
            .flagReason(null)
            .flagDetails(null)
            .build();

    stockUnit.setTenantId(tenantId);
    stockUnit.onCreate();
    return stockUnit;
  }

  // ── Domain Operations ─────────────────────────────────────────────────────

  /**
   * Consumes the given amount from this stock unit's current weight.
   *
   * <h3>Invariants enforced:</h3>
   *
   * <ul>
   *   <li>amount > 0 (no negative consumption)
   *   <li>amount <= currentWeight (cannot consume more than available)
   *   <li>status must be AVAILABLE or PARTIAL
   *   <li>Automatically transitions to PARTIAL or DEPLETED based on remaining weight
   * </ul>
   *
   * <p><b>Important:</b> The caller (service layer) is responsible for also calling {@code
   * Batch.consumeFromAvailable(amount)} in the same transaction to keep the lot-level aggregate
   * consistent.
   *
   * @param amount the weight to consume (must be positive)
   * @throws StockUnitDomainException if amount is invalid or status does not allow consumption
   * @throws InsufficientWeightException if amount exceeds currentWeight
   */
  public void consume(BigDecimal amount) {
    validatePositive(amount, "Consumption amount");
    if (!status.isConsumable()) {
      throw new StockUnitDomainException(
          String.format(
              "StockUnit %s cannot be consumed in status %s. Allowed: %s",
              barcode, status, StockUnitStatus.CONSUMABLE));
    }
    if (amount.compareTo(currentWeight) > 0) {
      throw new InsufficientWeightException(barcode, amount, currentWeight);
    }

    this.currentWeight = this.currentWeight.subtract(amount);
    recalculateStatusAfterWeightChange();
    onUpdate();
  }

  /**
   * Reverses a previous consumption — adds weight back to this stock unit.
   *
   * <h3>Invariants enforced:</h3>
   *
   * <ul>
   *   <li>amount > 0
   *   <li>amount <= (initialWeight - currentWeight) — cannot reverse more than what was consumed
   *   <li>Works from DEPLETED too (reversal is the one exception that lifts a terminal DEPLETED)
   * </ul>
   *
   * <p><b>Important:</b> The caller (service layer) is responsible for also calling {@code
   * Batch.adjustQuantity(+amount)} in the same transaction.
   *
   * @param amount the weight to add back (must be positive)
   * @param reason mandatory reason for audit log
   * @throws StockUnitDomainException if amount is invalid or exceeds consumed amount
   */
  public void reverseConsumption(BigDecimal amount, String reason) {
    if (this.status == StockUnitStatus.DISPOSED) {
      throw new StockUnitDomainException(
          "Cannot reverse consumption on DISPOSED StockUnit " + barcode);
    }
    validatePositive(amount, "Reversal amount");
    if (reason == null || reason.isBlank()) {
      throw new StockUnitDomainException("Reversal reason must not be blank");
    }

    BigDecimal consumedAmount = initialWeight.subtract(currentWeight);
    if (amount.compareTo(consumedAmount) > 0) {
      throw new StockUnitDomainException(
          String.format(
              "Cannot reverse %.3f %s from StockUnit %s: only %.3f %s was consumed.",
              amount, unit, barcode, consumedAmount, unit));
    }

    this.currentWeight = this.currentWeight.add(amount);
    recalculateStatusAfterWeightChange();
    onUpdate();
  }

  /**
   * Transfers this stock unit to a new warehouse location.
   *
   * <p>Sets the current location to IN_TRANSIT. The caller must call {@link #arriveAt(UUID)} when
   * the transfer is physically completed to finalize the location.
   *
   * @param targetLocationId the destination warehouse location
   * @throws StockUnitDomainException if unit is in a status that blocks transfers
   */
  public void startTransfer(UUID targetLocationId) {
    if (!status.canTransitionTo(StockUnitStatus.IN_TRANSIT)) {
      throw new StockUnitDomainException(
          String.format("StockUnit %s cannot transition to IN_TRANSIT from %s.", barcode, status));
    }
    this.previousLocationId = this.locationId;
    this.locationId = targetLocationId;
    this.status = StockUnitStatus.IN_TRANSIT;
    onUpdate();
  }

  /**
   * Completes a transfer — stock unit arrives at its destination.
   *
   * <p>Transitions from IN_TRANSIT to AVAILABLE (if full) or PARTIAL (if partially consumed).
   *
   * @param finalLocationId the final destination location (may differ from startTransfer target)
   * @throws StockUnitDomainException if unit is not IN_TRANSIT
   */
  public void arriveAt(UUID finalLocationId) {
    if (this.status != StockUnitStatus.IN_TRANSIT) {
      throw new StockUnitDomainException(
          String.format(
              "StockUnit %s cannot arrive: not IN_TRANSIT (current status: %s).", barcode, status));
    }
    this.locationId = finalLocationId;
    recalculateStatusAfterWeightChange();
    onUpdate();
  }

  /**
   * Changes the quality grade assignment for this stock unit.
   *
   * <p>Records the previous grade ID for traceability. The caller (service layer) is responsible
   * for enforcing the approval requirement by checking {@code
   * currentGrade.requiresApprovalForTransition(newGrade)} before calling this method.
   *
   * @param newGradeId the new quality grade ID
   */
  public void changeGrade(UUID newGradeId) {
    if (newGradeId == null) {
      throw new StockUnitDomainException("New quality grade ID must not be null");
    }
    this.previousGradeId = this.qualityGradeId;
    this.qualityGradeId = newGradeId;
    onUpdate();
  }

  /**
   * Places this stock unit on hold.
   *
   * @throws StockUnitDomainException if the current status does not allow transitioning to ON_HOLD
   */
  public void hold() {
    transitionStatus(StockUnitStatus.ON_HOLD);
  }

  /**
   * Releases this stock unit from hold back to its pre-hold state.
   *
   * @throws StockUnitDomainException if the current status is not ON_HOLD
   */
  public void releaseHold() {
    if (this.status != StockUnitStatus.ON_HOLD) {
      throw new StockUnitDomainException(
          String.format("StockUnit %s is not ON_HOLD (current: %s).", barcode, status));
    }
    recalculateStatusAfterWeightChange();
    onUpdate();
  }

  /**
   * Quarantines this stock unit — suspected quality issue.
   *
   * @throws StockUnitDomainException if the current status does not allow quarantine
   */
  public void quarantine() {
    transitionStatus(StockUnitStatus.QUARANTINE);
  }

  /**
   * Releases this stock unit from quarantine back to AVAILABLE.
   *
   * <p>Requires QC Manager trust level — enforced at the service layer.
   *
   * @throws StockUnitDomainException if the current status is not QUARANTINE
   */
  public void releaseQuarantine() {
    if (this.status != StockUnitStatus.QUARANTINE) {
      throw new StockUnitDomainException(
          String.format("StockUnit %s is not QUARANTINE (current: %s).", barcode, status));
    }
    recalculateStatusAfterWeightChange();
    onUpdate();
  }

  /**
   * Disposes this stock unit (scrap, destruction). Terminal — cannot be reversed.
   *
   * <p>Requires Admin trust level — enforced at the service layer.
   *
   * @throws StockUnitDomainException if the current status does not allow disposal
   */
  public void dispose() {
    transitionStatus(StockUnitStatus.DISPOSED);
  }

  /**
   * Reserves this stock unit for an order.
   *
   * @throws StockUnitDomainException if current status does not allow reservation
   */
  public void reserve() {
    transitionStatus(StockUnitStatus.RESERVED);
  }

  /**
   * Releases a reservation on this stock unit.
   *
   * @throws StockUnitDomainException if status is not RESERVED
   */
  public void releaseReservation() {
    if (this.status != StockUnitStatus.RESERVED) {
      throw new StockUnitDomainException(
          String.format("StockUnit %s is not RESERVED (current: %s).", barcode, status));
    }
    recalculateStatusAfterWeightChange();
    onUpdate();
  }

  /**
   * Raises an anomaly flag on this stock unit.
   *
   * @param reason the flag reason code (e.g. "WEIGHT_ANOMALY", "DECLARATION_MISMATCH")
   * @param details human-readable explanation
   */
  public void flag(String reason, String details) {
    if (reason == null || reason.isBlank()) {
      throw new StockUnitDomainException("Flag reason must not be blank");
    }
    this.flagged = true;
    this.flagReason = reason;
    this.flagDetails = details;
    onUpdate();
  }

  /** Clears the anomaly flag after operator review/approval. */
  public void clearFlag() {
    this.flagged = false;
    this.flagReason = null;
    this.flagDetails = null;
    onUpdate();
  }

  // ── Read-only Calculated Properties ──────────────────────────────────────

  /**
   * Returns the total weight that has been consumed from this unit.
   *
   * @return initialWeight - currentWeight
   */
  public BigDecimal getConsumedWeight() {
    return initialWeight.subtract(currentWeight);
  }

  /**
   * Returns the consumption percentage relative to initial weight.
   *
   * @return 0 if nothing consumed, 100.00 if fully depleted
   */
  public BigDecimal getConsumptionPercent() {
    if (initialWeight.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return getConsumedWeight()
        .multiply(new BigDecimal("100"))
        .divide(initialWeight, 2, java.math.RoundingMode.HALF_UP);
  }

  // ── Private Helpers ───────────────────────────────────────────────────────

  /**
   * Recalculates status based on current weight. Used after any weight change or lock release.
   *
   * <ul>
   *   <li>currentWeight == 0 → DEPLETED (terminal)
   *   <li>currentWeight == initialWeight → AVAILABLE (fully intact)
   *   <li>0 < currentWeight < initialWeight → PARTIAL
   * </ul>
   */
  private void recalculateStatusAfterWeightChange() {
    if (currentWeight.compareTo(BigDecimal.ZERO) == 0) {
      this.status = StockUnitStatus.DEPLETED;
    } else if (currentWeight.compareTo(initialWeight) == 0) {
      this.status = StockUnitStatus.AVAILABLE;
    } else {
      this.status = StockUnitStatus.PARTIAL;
    }
  }

  private void transitionStatus(StockUnitStatus target) {
    if (this.status == target) {
      return;
    }
    if (!this.status.canTransitionTo(target)) {
      throw new StockUnitDomainException(
          String.format(
              "StockUnit %s cannot transition from %s to %s.", barcode, this.status, target));
    }
    this.status = target;
    onUpdate();
  }

  private void validatePositive(BigDecimal value, String fieldName) {
    if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new StockUnitDomainException(
          fieldName + " must be positive for StockUnit " + barcode + ", got: " + value);
    }
  }

  @Override
  protected String getModuleCode() {
    return "SU";
  }
}
