package com.fabricmanagement.order.sales.domain;

/**
 * Sales order lifecycle status.
 *
 * <p>Flow: DRAFT → CONFIRMED → IN_PROGRESS → PARTIALLY_SHIPPED → SHIPPED → DELIVERED
 */
public enum OrderStatus {
  /** Initial state - order being prepared */
  DRAFT,

  /** Order confirmed by customer */
  CONFIRMED,

  /** Order is being processed */
  IN_PROGRESS,

  /** Some items shipped, some pending */
  PARTIALLY_SHIPPED,

  /** All items shipped */
  SHIPPED,

  /** Order delivered to customer */
  DELIVERED,

  /** Order cancelled */
  CANCELLED,

  /** Order on hold (payment issue, etc.) */
  ON_HOLD;

  /** Check if order is in a terminal state (cannot be modified). */
  public boolean isTerminal() {
    return this == DELIVERED || this == CANCELLED;
  }

  /** Check if order can be shipped. */
  public boolean canShip() {
    return this == CONFIRMED || this == IN_PROGRESS || this == PARTIALLY_SHIPPED;
  }

  /** Check if order can be cancelled. */
  public boolean canCancel() {
    return this == DRAFT || this == CONFIRMED || this == ON_HOLD;
  }
}
