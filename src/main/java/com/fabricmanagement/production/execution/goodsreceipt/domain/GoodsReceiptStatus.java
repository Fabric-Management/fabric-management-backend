package com.fabricmanagement.production.execution.goodsreceipt.domain;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle statuses for a GoodsReceipt.
 *
 * <pre>
 *   DRAFT → CONFIRMED  (terminal)
 * </pre>
 *
 * <p>DRAFT: receipt created, physical inspection in progress.
 *
 * <p>CONFIRMED: receipt finalised — IWM stock transaction triggered, source order updated.
 */
public enum GoodsReceiptStatus {

  /** Receipt created; awaiting physical inspection and data entry completion. */
  DRAFT,

  /** Receipt confirmed; stock entry and source order update triggered (terminal). */
  CONFIRMED;

  private static final Map<GoodsReceiptStatus, Set<GoodsReceiptStatus>> VALID_TRANSITIONS =
      Map.of(
          DRAFT, Set.of(CONFIRMED),
          CONFIRMED, Set.of()); // terminal

  /** Returns true if transition from this status to target is allowed. */
  public boolean canTransitionTo(GoodsReceiptStatus target) {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }
}
