package com.fabricmanagement.production.execution.batch.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * A named reservation that ties a specific quantity of a {@link Batch} to a reference entity (work
 * order, sample request, etc.).
 *
 * <p>This prevents the "blind reservation" problem where ad-hoc consumption could silently erode
 * reservations belonging to other work orders.
 *
 * <p>Invariant: {@code consumedQuantity <= reservedQuantity}.
 */
@Entity
@Table(name = "production_execution_batch_reservation", schema = "production")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchReservation extends BaseEntity {

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Column(name = "reference_id")
  private UUID referenceId;

  @Column(name = "reference_type", nullable = false, length = 50)
  private String referenceType;

  @Column(name = "reserved_quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal reservedQuantity;

  @Column(name = "consumed_quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal consumedQuantity;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ReservationStatus status;

  @Column(name = "reserved_at", nullable = false)
  private Instant reservedAt;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  public static BatchReservation create(
      UUID tenantId,
      UUID batchId,
      UUID referenceId,
      String referenceType,
      BigDecimal quantity,
      String unit,
      String remarks) {

    BatchReservation reservation = new BatchReservation();
    reservation.setTenantId(tenantId);
    reservation.setBatchId(batchId);
    reservation.setReferenceId(referenceId);
    reservation.setReferenceType(referenceType);
    reservation.setReservedQuantity(quantity);
    reservation.setConsumedQuantity(BigDecimal.ZERO);
    reservation.setUnit(unit);
    reservation.setStatus(ReservationStatus.ACTIVE);
    reservation.setReservedAt(Instant.now());
    reservation.setRemarks(remarks);
    reservation.onCreate();

    return reservation;
  }

  /** Quantity that is still reserved and not yet consumed. */
  public BigDecimal getRemainingQuantity() {
    return reservedQuantity.subtract(consumedQuantity);
  }

  /**
   * Consume a quantity against this reservation.
   *
   * @throws BatchDomainException if qty exceeds remaining or reservation is not consumable
   */
  public void consume(BigDecimal qty) {
    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Consumption amount must be positive");
    }
    if (this.status == ReservationStatus.FULFILLED || this.status == ReservationStatus.CANCELLED) {
      throw new BatchDomainException(
          String.format("Cannot consume from reservation %s: status is %s", getId(), this.status));
    }
    if (getRemainingQuantity().compareTo(qty) < 0) {
      throw new BatchDomainException(
          String.format(
              "Cannot consume %.3f %s from reservation %s: only %.3f %s remaining",
              qty, unit, getId(), getRemainingQuantity(), unit));
    }

    this.consumedQuantity = this.consumedQuantity.add(qty);

    if (this.consumedQuantity.compareTo(this.reservedQuantity) == 0) {
      this.status = ReservationStatus.FULFILLED;
    } else {
      this.status = ReservationStatus.PARTIALLY_CONSUMED;
    }
    onUpdate();
  }

  /**
   * Cancel this reservation and return the remaining (unconsumed) quantity.
   *
   * @return the quantity that should be released back to the batch
   */
  public BigDecimal cancel() {
    if (this.status == ReservationStatus.FULFILLED) {
      throw new BatchDomainException("Cannot cancel a fully fulfilled reservation: " + getId());
    }
    if (this.status == ReservationStatus.CANCELLED) {
      throw new BatchDomainException("Reservation is already cancelled: " + getId());
    }

    BigDecimal remaining = getRemainingQuantity();
    this.status = ReservationStatus.CANCELLED;
    onUpdate();
    return remaining;
  }

  /**
   * Complete this reservation explicitly, releasing any remaining unconsumed quantity.
   *
   * @return the quantity that should be released back to the batch
   */
  public BigDecimal complete() {
    if (this.status == ReservationStatus.FULFILLED) {
      throw new BatchDomainException("Reservation is already fulfilled: " + getId());
    }
    if (this.status == ReservationStatus.CANCELLED) {
      throw new BatchDomainException("Cannot complete a cancelled reservation: " + getId());
    }

    BigDecimal remaining = getRemainingQuantity();
    this.status = ReservationStatus.FULFILLED;
    onUpdate();
    return remaining;
  }

  @Override
  protected String getModuleCode() {
    return "EXEC-FBRES";
  }
}
