package com.fabricmanagement.procurement.purchaseorder.domain;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle statuses for a PurchaseOrder.
 *
 * <pre>
 * DRAFT → SENT → CONFIRMED → PARTIALLY_RECEIVED → RECEIVED → CLOSED
 *                           ↘ CANCELLED
 * </pre>
 */
public enum PurchaseOrderStatus {
  DRAFT,
  SENT,
  CONFIRMED,
  PARTIALLY_RECEIVED,
  RECEIVED,
  CLOSED,
  CANCELLED;

  private static final Map<PurchaseOrderStatus, Set<PurchaseOrderStatus>> VALID_TRANSITIONS =
      Map.ofEntries(
          Map.entry(DRAFT, Set.of(SENT, CANCELLED)),
          Map.entry(SENT, Set.of(CONFIRMED, CANCELLED)),
          Map.entry(CONFIRMED, Set.of(PARTIALLY_RECEIVED, RECEIVED, CANCELLED)),
          Map.entry(PARTIALLY_RECEIVED, Set.of(RECEIVED, CANCELLED)),
          Map.entry(RECEIVED, Set.of(CLOSED)),
          Map.entry(CLOSED, Set.of()), // terminal
          Map.entry(CANCELLED, Set.of())); // terminal

  /** Returns true if transition from this status to target is allowed. */
  public boolean canTransitionTo(PurchaseOrderStatus target) {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }
}
