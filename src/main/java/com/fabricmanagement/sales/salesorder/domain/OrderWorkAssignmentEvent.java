package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
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
import org.hibernate.annotations.Immutable;

/** One routing, claim, assignment or release of order work, append-only, with its reason. */
@Entity
@Immutable
@Table(name = "order_work_assignment_event", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderWorkAssignmentEvent extends BaseEntity {

  public static final int MAX_REASON_LENGTH = 1000;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "work_kind", nullable = false, updatable = false, length = 30)
  private OrderWorkKind kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, updatable = false, length = 20)
  private OrderWorkEventType type;

  @Column(name = "department_code", nullable = false, updatable = false, length = 50)
  private String departmentCode;

  @Column(name = "from_assignee_id", updatable = false)
  private UUID fromAssigneeId;

  @Column(name = "to_assignee_id", updatable = false)
  private UUID toAssigneeId;

  @Column(name = "reason", updatable = false, length = MAX_REASON_LENGTH)
  private String reason;

  /** Null only when the system routed the work as part of a flow step. */
  @Column(name = "actor_id", updatable = false)
  private UUID actorId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  public static OrderWorkAssignmentEvent of(
      OrderWorkAssignment assignment,
      OrderWorkEventType type,
      UUID fromAssignee,
      String reason,
      UUID actorId,
      Instant occurredAt) {
    if (assignment == null || type == null || occurredAt == null) {
      throw new IllegalArgumentException("Assignment, type and time are required");
    }
    String trimmed = reason == null || reason.isBlank() ? null : reason.trim();
    if ((type == OrderWorkEventType.ASSIGNED || type == OrderWorkEventType.RELEASED)
        && trimmed == null) {
      throw new IllegalArgumentException("Assigning or releasing work needs a reason");
    }
    OrderWorkAssignmentEvent value = new OrderWorkAssignmentEvent();
    value.salesOrderId = assignment.getSalesOrderId();
    value.kind = assignment.getKind();
    value.type = type;
    value.departmentCode = assignment.getDepartmentCode();
    value.fromAssigneeId = fromAssignee;
    value.toAssigneeId = assignment.getAssigneeId();
    value.reason = trimmed;
    value.actorId = actorId;
    value.occurredAt = occurredAt;
    return value;
  }

  @Override
  protected String getModuleCode() {
    return "OWE";
  }
}
