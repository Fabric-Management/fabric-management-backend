package com.fabricmanagement.procurement.subcontract.domain;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle statuses for a SubcontractOrder.
 *
 * <pre>
 * DRAFT → CONFIRMED → PRODUCT_SENT → IN_PROGRESS → COMPLETED → CLOSED
 *                   ↘ CANCELLED
 * </pre>
 */
public enum SubcontractOrderStatus {

  /** Order created, not yet confirmed with subcontractor. */
  DRAFT,

  /** Subcontractor accepted the order. */
  CONFIRMED,

  /** Raw products dispatched to subcontractor. */
  PRODUCT_SENT,

  /** Subcontractor actively working on the order. */
  IN_PROGRESS,

  /**
   * Finished goods returned to warehouse via GoodsReceipt. Waste/shrinkage calculated at this
   * stage.
   */
  COMPLETED,

  /** Order closed after final reconciliation. Terminal. */
  CLOSED,

  /** Order cancelled before product dispatch. Terminal. */
  CANCELLED;

  private static final Map<SubcontractOrderStatus, Set<SubcontractOrderStatus>> VALID_TRANSITIONS =
      Map.ofEntries(
          Map.entry(DRAFT, Set.of(CONFIRMED, CANCELLED)),
          Map.entry(CONFIRMED, Set.of(PRODUCT_SENT, CANCELLED)),
          Map.entry(PRODUCT_SENT, Set.of(IN_PROGRESS)),
          Map.entry(IN_PROGRESS, Set.of(COMPLETED)),
          Map.entry(COMPLETED, Set.of(CLOSED)),
          Map.entry(CLOSED, Set.of()),
          Map.entry(CANCELLED, Set.of()));

  public boolean canTransitionTo(SubcontractOrderStatus target) {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }
}
