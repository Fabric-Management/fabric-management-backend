package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * Planning's or the dyehouse's confirmation that part of a line can be made from greige already
 * available (SOI A08, R13, K10). The quantity is finished goods in the line unit; greige width,
 * finished width and yield are never substituted for each other. The history estimate shown at the
 * time is kept for traceability; the confirmation, not the estimate, makes the portion count.
 */
@Entity
@Table(name = "line_greige_cover", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineGreigeCover extends BaseEntity {

  public enum Status {
    ACTIVE,
    WITHDRAWN
  }

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "sales_order_line_id", nullable = false, updatable = false)
  private UUID salesOrderLineId;

  @Column(name = "finished_qty", nullable = false, updatable = false, precision = 15, scale = 3)
  private BigDecimal finishedQty;

  @Column(name = "unit", nullable = false, updatable = false, length = 20)
  private String unit;

  @Type(JsonType.class)
  @Column(
      name = "greige_batch_ids",
      nullable = false,
      updatable = false,
      columnDefinition = "jsonb")
  private List<UUID> greigeBatchIds = new ArrayList<>();

  @Column(name = "basis_note", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String basisNote;

  @Column(name = "estimate_sample_size", nullable = false, updatable = false)
  private long estimateSampleSize;

  @Column(name = "estimate_avg_yield_pct", updatable = false, precision = 7, scale = 2)
  private BigDecimal estimateAvgYieldPct;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status;

  @Column(name = "confirmed_by", nullable = false, updatable = false)
  private UUID confirmedBy;

  @Column(name = "confirmed_at", nullable = false, updatable = false)
  private Instant confirmedAt;

  @Column(name = "withdrawn_by")
  private UUID withdrawnBy;

  @Column(name = "withdrawn_at")
  private Instant withdrawnAt;

  public static LineGreigeCover confirm(
      UUID salesOrderId,
      UUID salesOrderLineId,
      BigDecimal finishedQty,
      String unit,
      List<UUID> greigeBatchIds,
      String basisNote,
      long estimateSampleSize,
      BigDecimal estimateAvgYieldPct,
      UUID confirmedBy,
      Instant confirmedAt) {
    if (salesOrderId == null
        || salesOrderLineId == null
        || confirmedBy == null
        || confirmedAt == null) {
      throw new IllegalArgumentException("Order, line, confirmer and time are required");
    }
    if (finishedQty == null || finishedQty.signum() <= 0 || unit == null || unit.isBlank()) {
      throw new IllegalArgumentException(
          "A positive finished quantity in the line unit is required");
    }
    if (basisNote == null || basisNote.isBlank()) {
      throw new IllegalArgumentException(
          "State what the confirmation rests on: greige available and process conditions");
    }
    LineGreigeCover cover = new LineGreigeCover();
    cover.salesOrderId = salesOrderId;
    cover.salesOrderLineId = salesOrderLineId;
    cover.finishedQty = finishedQty;
    cover.unit = unit;
    cover.greigeBatchIds =
        greigeBatchIds == null ? new ArrayList<>() : new ArrayList<>(greigeBatchIds);
    cover.basisNote = basisNote.trim();
    cover.estimateSampleSize = estimateSampleSize;
    cover.estimateAvgYieldPct = estimateAvgYieldPct;
    cover.status = Status.ACTIVE;
    cover.confirmedBy = confirmedBy;
    cover.confirmedAt = confirmedAt;
    return cover;
  }

  public void withdraw(UUID actor, Instant at) {
    if (status != Status.ACTIVE) {
      throw new IllegalStateException("Only an active greige cover can be withdrawn");
    }
    this.status = Status.WITHDRAWN;
    this.withdrawnBy = actor;
    this.withdrawnAt = at;
  }

  @Override
  protected String getModuleCode() {
    return "LGC";
  }
}
