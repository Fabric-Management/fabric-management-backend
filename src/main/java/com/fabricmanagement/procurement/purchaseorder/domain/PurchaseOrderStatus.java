package com.fabricmanagement.procurement.purchaseorder.domain;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle statuses for a PurchaseOrder.
 *
 * <pre>
 * DRAFT → PENDING_APPROVAL → SENT → CONFIRMED → PARTIALLY_RECEIVED → RECEIVED → CLOSED
 *         ↘ REJECTED         ↘ CANCELLED
 *         ↘ CANCELLED
 * </pre>
 */
public enum PurchaseOrderStatus {
  DRAFT,
  PENDING_APPROVAL,
  SENT,
  CONFIRMED,
  PARTIALLY_RECEIVED,
  RECEIVED,
  CLOSED,
  CANCELLED,
  REJECTED;

  private static final Map<PurchaseOrderStatus, Set<PurchaseOrderStatus>> VALID_TRANSITIONS =
      Map.ofEntries(
          Map.entry(DRAFT, Set.of(PENDING_APPROVAL, SENT, CANCELLED)),
          Map.entry(PENDING_APPROVAL, Set.of(SENT, REJECTED, CANCELLED)),
          Map.entry(SENT, Set.of(CONFIRMED, CANCELLED)),
          Map.entry(CONFIRMED, Set.of(PARTIALLY_RECEIVED, RECEIVED, CANCELLED)),
          Map.entry(PARTIALLY_RECEIVED, Set.of(RECEIVED, CANCELLED)),
          Map.entry(RECEIVED, Set.of(CLOSED)),
          Map.entry(CLOSED, Set.of()), // terminal
          Map.entry(CANCELLED, Set.of()), // terminal
          Map.entry(REJECTED, Set.of(DRAFT, CANCELLED))); // revise and resubmit allowed

  /** Returns true if transition from this status to target is allowed. */
  public boolean canTransitionTo(PurchaseOrderStatus target) {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }
}
