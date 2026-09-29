package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * When a portion of a line will be ready to ship (SOI K13, R15, A07, A07-b): requested by sales,
 * confirmed by planning for production portions and by the warehouse for stock. A readiness date is
 * not a delivery date.
 */
@Entity
@Table(name = "line_portion_readiness", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinePortionReadiness extends BaseEntity {

  @Schema(name = "PortionReadinessStatus", enumAsRef = true)
  public enum Status {
    REQUESTED,
    CONFIRMED,
    WITHDRAWN
  }

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "sales_order_line_id", nullable = false, updatable = false)
  private UUID salesOrderLineId;

  @Enumerated(EnumType.STRING)
  @Column(name = "portion", nullable = false, updatable = false, length = 20)
  private CoverPortionKind portion;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status;

  @Column(name = "requested_by")
  private UUID requestedBy;

  @Column(name = "requested_at")
  private Instant requestedAt;

  @Column(name = "ready_on")
  private LocalDate readyOn;

  @Column(name = "basis_note", columnDefinition = "TEXT")
  private String basisNote;

  @Column(name = "confirmed_by")
  private UUID confirmedBy;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  public static LinePortionReadiness request(
      UUID salesOrderId, UUID lineId, CoverPortionKind portion, UUID actor, Instant at) {
    if (salesOrderId == null || lineId == null || portion == null || actor == null || at == null) {
      throw new IllegalArgumentException("Order, line, portion, requester and time are required");
    }
    LinePortionReadiness readiness = new LinePortionReadiness();
    readiness.salesOrderId = salesOrderId;
    readiness.salesOrderLineId = lineId;
    readiness.portion = portion;
    readiness.status = Status.REQUESTED;
    readiness.requestedBy = actor;
    readiness.requestedAt = at;
    return readiness;
  }

  public static LinePortionReadiness unrequested(
      UUID salesOrderId, UUID lineId, CoverPortionKind portion) {
    LinePortionReadiness readiness = new LinePortionReadiness();
    readiness.salesOrderId = salesOrderId;
    readiness.salesOrderLineId = lineId;
    readiness.portion = portion;
    readiness.status = Status.REQUESTED;
    return readiness;
  }

  public void confirm(LocalDate readyOn, String basisNote, UUID actor, Instant at) {
    if (status == Status.WITHDRAWN) {
      throw new IllegalStateException("A withdrawn readiness record cannot be confirmed");
    }
    if (readyOn == null || actor == null || at == null) {
      throw new IllegalArgumentException("Ready date, confirmer and time are required");
    }
    if (basisNote == null || basisNote.isBlank()) {
      throw new IllegalArgumentException("State what the readiness date rests on");
    }
    this.readyOn = readyOn;
    this.basisNote = basisNote.trim();
    this.confirmedBy = actor;
    this.confirmedAt = at;
    this.status = Status.CONFIRMED;
  }

  public void withdraw() {
    this.status = Status.WITHDRAWN;
  }

  public boolean isOpen() {
    return status != Status.WITHDRAWN;
  }

  @Override
  protected String getModuleCode() {
    return "LPR";
  }
}
