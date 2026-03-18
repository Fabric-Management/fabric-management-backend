package com.fabricmanagement.production.execution.workorder.domain;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle statuses for a WorkOrder.
 *
 * <pre>
 * TRUSTED/STANDARD user (no approval policy):
 *   DRAFT → SENT → IN_PROGRESS → COMPLETED
 *
 * PROBATION/STANDARD user (with approval policy):
 *   DRAFT → PENDING_APPROVAL → APPROVED → SENT → IN_PROGRESS → COMPLETED
 *                            ↘ REJECTED → DRAFT
 *
 * Cancellation: any non-COMPLETED status → CANCELLED
 * </pre>
 */
public enum WorkOrderStatus {
  DRAFT,
  PENDING_APPROVAL,
  APPROVED,
  REJECTED,
  SENT,
  IN_PROGRESS,
  COMPLETED,
  CANCELLED;

  private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> VALID_TRANSITIONS =
      Map.ofEntries(
          Map.entry(DRAFT, Set.of(PENDING_APPROVAL, SENT, CANCELLED)),
          Map.entry(PENDING_APPROVAL, Set.of(APPROVED, REJECTED, CANCELLED)),
          Map.entry(APPROVED, Set.of(SENT, CANCELLED)),
          Map.entry(REJECTED, Set.of(DRAFT, CANCELLED)),
          Map.entry(SENT, Set.of(IN_PROGRESS, CANCELLED)),
          Map.entry(IN_PROGRESS, Set.of(COMPLETED, CANCELLED)),
          Map.entry(COMPLETED, Set.of()), // terminal
          Map.entry(CANCELLED, Set.of())); // terminal

  /** Returns true if transition from this status to target is allowed. */
  public boolean canTransitionTo(WorkOrderStatus target) {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }
}
