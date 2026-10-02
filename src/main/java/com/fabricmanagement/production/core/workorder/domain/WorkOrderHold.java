package com.fabricmanagement.production.core.workorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.core.workorder.domain.exception.WorkOrderHoldException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A hold on running work for a sales-order line (SOI K19, R20, A12). Sales requests it; the
 * production owner confirms that work physically stopped; production or planning resumes once the
 * customer change is settled and the technical and material checks are done. A request is not a
 * stop, and neither is a cancellation. The work order's own status is not changed here.
 */
@Entity
@Table(name = "work_order_hold", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrderHold extends BaseEntity {

  @Schema(name = "WorkOrderHoldStatus", enumAsRef = true)
  public enum Status {
    HOLD_REQUESTED,
    HOLD_CONFIRMED,
    RESUMED,
    WITHDRAWN
  }

  @Column(name = "work_order_id", nullable = false, updatable = false)
  private UUID workOrderId;

  @Column(name = "sales_order_id", updatable = false)
  private UUID salesOrderId;

  @Column(name = "sales_order_line_id", nullable = false, updatable = false)
  private UUID salesOrderLineId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status;

  @Column(name = "request_reason", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String requestReason;

  @Column(name = "requested_by", nullable = false, updatable = false)
  private UUID requestedBy;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Column(name = "stop_note", columnDefinition = "TEXT")
  private String stopNote;

  @Column(name = "confirmed_by")
  private UUID confirmedBy;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "resume_note", columnDefinition = "TEXT")
  private String resumeNote;

  @Column(name = "resumed_by")
  private UUID resumedBy;

  @Column(name = "resumed_at")
  private Instant resumedAt;

  public static WorkOrderHold request(
      UUID workOrderId,
      UUID salesOrderId,
      UUID salesOrderLineId,
      String reason,
      UUID requestedBy,
      Instant requestedAt) {
    if (workOrderId == null
        || salesOrderLineId == null
        || requestedBy == null
        || requestedAt == null) {
      throw new IllegalArgumentException("Work order, line, requester and time are required");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Say why the work should be held");
    }
    WorkOrderHold hold = new WorkOrderHold();
    hold.workOrderId = workOrderId;
    hold.salesOrderId = salesOrderId;
    hold.salesOrderLineId = salesOrderLineId;
    hold.status = Status.HOLD_REQUESTED;
    hold.requestReason = reason.trim();
    hold.requestedBy = requestedBy;
    hold.requestedAt = requestedAt;
    return hold;
  }

  /** The production owner confirms the physical stop (A12). */
  public void confirmStop(String note, UUID actor, Instant at) {
    if (status != Status.HOLD_REQUESTED) {
      throw new IllegalStateException("Only a requested hold can be confirmed; it is " + status);
    }
    if (actor == null || at == null || note == null || note.isBlank()) {
      throw new IllegalArgumentException("Say where the work stopped");
    }
    this.status = Status.HOLD_CONFIRMED;
    this.stopNote = note.trim();
    this.confirmedBy = actor;
    this.confirmedAt = at;
  }

  /** Resumes after the customer change is settled and the checks are complete (A12). */
  public void resume(
      boolean customerChangeSettled, boolean checksCompleted, String note, UUID actor, Instant at) {
    if (status != Status.HOLD_CONFIRMED) {
      throw new IllegalStateException("Only a confirmed hold can be resumed; it is " + status);
    }
    if (!customerChangeSettled) {
      throw WorkOrderHoldException.customerChangeOpen();
    }
    if (!checksCompleted) {
      throw WorkOrderHoldException.checksIncomplete();
    }
    if (actor == null || at == null || note == null || note.isBlank()) {
      throw new IllegalArgumentException("Record what was checked before resuming");
    }
    this.status = Status.RESUMED;
    this.resumeNote = note.trim();
    this.resumedBy = actor;
    this.resumedAt = at;
  }

  public void withdraw() {
    if (status != Status.HOLD_REQUESTED) {
      throw new IllegalStateException("Only a requested hold can be withdrawn");
    }
    this.status = Status.WITHDRAWN;
  }

  public boolean isOpen() {
    return status == Status.HOLD_REQUESTED || status == Status.HOLD_CONFIRMED;
  }

  @Override
  protected String getModuleCode() {
    return "WOH";
  }
}
