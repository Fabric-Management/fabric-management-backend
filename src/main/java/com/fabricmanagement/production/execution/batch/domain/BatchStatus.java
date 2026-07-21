package com.fabricmanagement.production.execution.batch.domain;

import java.util.Set;

/**
 * Lifecycle statuses for a Batch.
 *
 * <pre>
 * Production flow:
 *   PENDING_QC ──→ AVAILABLE ──reserve()──▶ RESERVED ──markInUse()──▶ IN_PROGRESS ──markDepleted()──▶ DEPLETED
 *        │            │                       │  │                        │
 *        ├─→ QUARANTINE ──→ AVAILABLE | QC_REJECTED | RETURNED | DESTROYED
 *        └─→ QC_REJECTED ──→ RETURNED | DESTROYED | AVAILABLE (override only)
 *
 *   AVAILABLE ←→ ON_HOLD (manual hold; reservation preserved in ON_HOLD)
 *   RESERVED ──→ ON_HOLD (hold with reservation)
 *
 * PENDING_QC  : Received, awaiting quality control.
 * QUARANTINE  : Suspicious lot, held for review.
 * AVAILABLE   : QC approved, stock freely available.
 * ON_HOLD     : Manual hold (dispute, investigation).
 * RESERVED    : Quantity committed to a production order.
 * IN_PROGRESS : Batch actively being consumed.
 * DEPLETED    : All quantity consumed (terminal).
 * QC_REJECTED : Quality rejected, return/destroy flow.
 * RETURNED    : Returned to supplier (terminal).
 * DESTROYED   : Destroyed/write-off (terminal).
 * </pre>
 */
public enum BatchStatus {
  /**
   * Lot received, awaiting quality control. Default for new batches.
   *
   * <p>Transitions: {@link #AVAILABLE} (QC approved), {@link #QC_REJECTED} (QC failed), {@link
   * #QUARANTINE} (suspicious, needs further review).
   */
  PENDING_QC,

  /**
   * Suspicious lot held for review. May indicate non-conformance or supplier dispute.
   *
   * <p>Transitions: {@link #AVAILABLE} (cleared), {@link #QC_REJECTED} (confirmed reject), {@link
   * #RETURNED} (return to supplier), {@link #DESTROYED} (write-off).
   */
  QUARANTINE,

  /**
   * QC approved. Stock is freely available for reservation and consumption.
   *
   * <p>Transitions: {@link #RESERVED} (reserve for order), {@link #ON_HOLD} (manual hold).
   */
  AVAILABLE,

  /**
   * Manual hold. Batch temporarily blocked (e.g. dispute, investigation, audit). Reservation is
   * preserved when transitioning to ON_HOLD.
   *
   * <p>Transitions: {@link #AVAILABLE} (hold released), {@link #RESERVED} (keep reservation).
   */
  ON_HOLD,

  /**
   * Quantity committed to a production order. Reserved stock cannot be used by other orders.
   *
   * <p>Transitions: {@link #AVAILABLE} (release), {@link #ON_HOLD} (hold with reservation), {@link
   * #IN_PROGRESS} (start production).
   */
  RESERVED,

  /**
   * Batch actively being consumed on the production floor.
   *
   * <p>Transitions: {@link #DEPLETED} (all quantity consumed).
   */
  IN_PROGRESS,

  /**
   * All quantity consumed. Batch closed (terminal state).
   *
   * <p>Transitions: none (terminal).
   */
  DEPLETED,

  /**
   * Quality rejected. Batch cannot be used; awaits return or destruction.
   *
   * <p>Transitions: {@link #RETURNED} (sent back to supplier), {@link #DESTROYED} (write-off),
   * {@link #AVAILABLE} (override only — supervisor approval, logged in override_log).
   */
  QC_REJECTED,

  /**
   * Returned to supplier. Batch closed (terminal state).
   *
   * <p>Transitions: none (terminal).
   */
  RETURNED,

  /**
   * Destroyed or written off. Batch closed (terminal state).
   *
   * <p>Transitions: none (terminal).
   */
  DESTROYED;

  /**
   * Statuses that indicate a fiber is committed to or actively used in production.
   *
   * <p>Used by {@code FiberService.deactivateFiber()} to block deactivation of a fiber that still
   * has live batches on the production floor.
   */
  public static final Set<BatchStatus> PRODUCTION_ACTIVE = Set.of(RESERVED, IN_PROGRESS);

  /** Statuses that block production use (reserve, consume). */
  public static final Set<BatchStatus> BLOCKED_FOR_PRODUCTION =
      Set.of(PENDING_QC, QUARANTINE, ON_HOLD, QC_REJECTED, RETURNED, DESTROYED);

  /**
   * True operational blockers for consumption of a specific RELEASED StockUnit. QC projection
   * statuses are deliberately absent because unit disposition is authoritative on that path.
   */
  public static final Set<BatchStatus> BLOCKED_FOR_RELEASED_UNIT_CONSUMPTION =
      Set.of(ON_HOLD, DEPLETED, RETURNED, DESTROYED);

  /**
   * State machine transition rules for {@link
   * com.fabricmanagement.production.execution.batch.domain.Batch#transitionStatus}.
   *
   * <p>QC_REJECTED → AVAILABLE is allowed only via override (service layer logs to override_log).
   */
  private static final java.util.Map<BatchStatus, Set<BatchStatus>> VALID_TRANSITIONS =
      java.util.Map.ofEntries(
          java.util.Map.entry(PENDING_QC, Set.of(AVAILABLE, QUARANTINE, QC_REJECTED)),
          java.util.Map.entry(QUARANTINE, Set.of(AVAILABLE, QC_REJECTED, RETURNED, DESTROYED)),
          java.util.Map.entry(AVAILABLE, Set.of(RESERVED, ON_HOLD)),
          java.util.Map.entry(ON_HOLD, Set.of(AVAILABLE, RESERVED)),
          java.util.Map.entry(RESERVED, Set.of(AVAILABLE, ON_HOLD, IN_PROGRESS)),
          java.util.Map.entry(IN_PROGRESS, Set.of(DEPLETED)),
          java.util.Map.entry(DEPLETED, Set.of()),
          java.util.Map.entry(QC_REJECTED, Set.of(RETURNED, DESTROYED, AVAILABLE)),
          java.util.Map.entry(RETURNED, Set.of()),
          java.util.Map.entry(DESTROYED, Set.of()));

  /**
   * Returns true if transition from this status to target is allowed.
   *
   * @param target the desired target status
   * @return true if transition is valid
   */
  public boolean canTransitionTo(BatchStatus target) {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }
}
