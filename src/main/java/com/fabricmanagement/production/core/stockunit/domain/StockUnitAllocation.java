package com.fabricmanagement.production.core.stockunit.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A whole piece held for one sales-order line (SOI D4, TK-4). The lot-level counter is kept by the
 * piece's {@code BatchReservation}; this record names the piece so no other line can take it. The
 * database allows one ACTIVE allocation per piece.
 */
@Entity
@Table(name = "stock_unit_allocation", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockUnitAllocation extends BaseEntity {

  @Column(name = "stock_unit_id", nullable = false, updatable = false)
  private UUID stockUnitId;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Column(name = "batch_reservation_id", nullable = false, updatable = false)
  private UUID batchReservationId;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "sales_order_line_id", nullable = false, updatable = false)
  private UUID salesOrderLineId;

  @Column(name = "quantity", nullable = false, updatable = false, precision = 15, scale = 3)
  private BigDecimal quantity;

  @Column(name = "unit", nullable = false, updatable = false, length = 20)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private StockUnitAllocationStatus status;

  @Column(name = "allocated_by", nullable = false, updatable = false)
  private UUID allocatedBy;

  @Column(name = "allocated_at", nullable = false, updatable = false)
  private Instant allocatedAt;

  @Column(name = "released_by")
  private UUID releasedBy;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Column(name = "release_reason", length = 60)
  private String releaseReason;

  public static StockUnitAllocation allocate(
      UUID stockUnitId,
      UUID batchId,
      UUID batchReservationId,
      UUID salesOrderId,
      UUID salesOrderLineId,
      BigDecimal quantity,
      String unit,
      UUID actor,
      Instant at) {
    if (stockUnitId == null
        || batchId == null
        || batchReservationId == null
        || salesOrderId == null
        || salesOrderLineId == null) {
      throw new IllegalArgumentException("Piece, lot, reservation, order and line are required");
    }
    if (quantity == null || quantity.signum() <= 0 || unit == null || unit.isBlank()) {
      throw new IllegalArgumentException("A positive measured quantity and its unit are required");
    }
    if (actor == null || at == null) {
      throw new IllegalArgumentException("Actor and time are required");
    }
    StockUnitAllocation allocation = new StockUnitAllocation();
    allocation.stockUnitId = stockUnitId;
    allocation.batchId = batchId;
    allocation.batchReservationId = batchReservationId;
    allocation.salesOrderId = salesOrderId;
    allocation.salesOrderLineId = salesOrderLineId;
    allocation.quantity = quantity;
    allocation.unit = unit;
    allocation.status = StockUnitAllocationStatus.ACTIVE;
    allocation.allocatedBy = actor;
    allocation.allocatedAt = at;
    return allocation;
  }

  public void release(UUID actor, Instant at, String reason) {
    if (status != StockUnitAllocationStatus.ACTIVE) {
      throw new IllegalStateException("Only an active allocation can be released");
    }
    if (actor == null || at == null || reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Actor, time and reason are required");
    }
    this.status = StockUnitAllocationStatus.RELEASED;
    this.releasedBy = actor;
    this.releasedAt = at;
    this.releaseReason = reason.trim();
  }

  public boolean isActiveAllocation() {
    return status == StockUnitAllocationStatus.ACTIVE;
  }

  @Override
  protected String getModuleCode() {
    return "SUA";
  }
}
