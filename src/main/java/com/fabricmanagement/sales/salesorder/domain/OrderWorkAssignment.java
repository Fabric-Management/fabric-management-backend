package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
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
 * Who is responsible for one kind of work on an order: the team it is routed to and, once someone
 * claims it or is assigned, the person. Being responsible gives the person scope over the order for
 * that work only; the permission to do the work is still required. Every change is recorded in
 * {@link OrderWorkAssignmentEvent}.
 */
@Entity
@Table(name = "order_work_assignment", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderWorkAssignment extends BaseEntity {

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "work_kind", nullable = false, updatable = false, length = 30)
  private OrderWorkKind kind;

  @Column(name = "department_code", nullable = false, length = 50)
  private String departmentCode;

  @Column(name = "routed_at", nullable = false)
  private Instant routedAt;

  @Column(name = "assignee_id")
  private UUID assigneeId;

  @Column(name = "assigned_at")
  private Instant assignedAt;

  public static OrderWorkAssignment route(
      UUID salesOrderId, OrderWorkKind kind, String departmentCode, Instant now) {
    if (salesOrderId == null || kind == null || departmentCode == null || now == null) {
      throw new IllegalArgumentException("Order, kind, department and time are required");
    }
    OrderWorkAssignment value = new OrderWorkAssignment();
    value.salesOrderId = salesOrderId;
    value.kind = kind;
    value.departmentCode = departmentCode;
    value.routedAt = now;
    return value;
  }

  public boolean isAssigned() {
    return assigneeId != null;
  }

  public boolean isAssignedTo(UUID userId) {
    return assigneeId != null && assigneeId.equals(userId);
  }

  /** Puts unassigned work back in the team's queue as new; assigned work keeps its person. */
  public void reroute(String department, Instant now) {
    if (isAssigned()) {
      throw new IllegalStateException("Assigned work is released before it is routed again");
    }
    this.departmentCode = department;
    this.routedAt = now;
  }

  /** Only one person can take unassigned work; a second claim is a conflict. */
  public void claim(UUID userId, Instant now) {
    if (isAssigned()) {
      throw OrderDomainException.workTaken(
          isAssignedTo(userId)
              ? "You already hold this work"
              : "Someone else took this work a moment ago");
    }
    this.assigneeId = userId;
    this.assignedAt = now;
  }

  public void assign(UUID userId, Instant now) {
    if (isAssignedTo(userId)) {
      throw new OrderDomainException("The work is already assigned to this person");
    }
    this.assigneeId = userId;
    this.assignedAt = now;
  }

  public void release() {
    if (!isAssigned()) {
      throw new OrderDomainException("The work is not assigned to anyone", 409);
    }
    this.assigneeId = null;
    this.assignedAt = null;
  }

  @Override
  protected String getModuleCode() {
    return "OWA";
  }
}
