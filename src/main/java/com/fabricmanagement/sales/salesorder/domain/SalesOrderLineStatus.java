package com.fabricmanagement.sales.salesorder.domain;

import java.util.Set;

/**
 * Lifecycle status for a SalesOrderLine.
 *
 * <pre>
 * PENDING → RECIPE_ASSIGNED → IN_PRODUCTION → COMPLETED → IN_WAREHOUSE → SHIPPED
 *         ↘ CANCELLED
 * </pre>
 */
public enum SalesOrderLineStatus {

  /** Line created, waiting for recipe assignment. */
  PENDING,

  /** Recipe found by RuleEngine or manually assigned. */
  RECIPE_ASSIGNED,

  /** At least one linked WorkOrder is IN_PROGRESS. */
  IN_PRODUCTION,

  /** All linked WorkOrders COMPLETED. */
  COMPLETED,

  /** Finished goods stored in warehouse (IWM ProductStoredEvent). */
  IN_WAREHOUSE,

  /** Goods shipped to customer (IWM ShipmentDispatchedEvent). Terminal. */
  SHIPPED,

  /** Line cancelled. Terminal. */
  CANCELLED;

  private static final java.util.Map<SalesOrderLineStatus, Set<SalesOrderLineStatus>>
      VALID_TRANSITIONS =
          java.util.Map.ofEntries(
              java.util.Map.entry(PENDING, Set.of(RECIPE_ASSIGNED, CANCELLED)),
              java.util.Map.entry(RECIPE_ASSIGNED, Set.of(IN_PRODUCTION, CANCELLED)),
              java.util.Map.entry(IN_PRODUCTION, Set.of(COMPLETED)),
              java.util.Map.entry(COMPLETED, Set.of(IN_WAREHOUSE)),
              java.util.Map.entry(IN_WAREHOUSE, Set.of(SHIPPED)),
              java.util.Map.entry(SHIPPED, Set.of()),
              java.util.Map.entry(CANCELLED, Set.of()));

  public boolean canTransitionTo(SalesOrderLineStatus target) {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }

  public boolean isTerminal() {
    return this == SHIPPED || this == CANCELLED;
  }
}
