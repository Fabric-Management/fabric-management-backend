package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A piece of work other teams do on a sales order, each with its own responsible team and person:
 * planning evaluates it, the warehouse confirms when held stock is ready to ship, logistics records
 * the sourced arrival estimate. Responsibility is independent of the order's sales owner.
 */
@Schema(name = "OrderWorkKind", enumAsRef = true)
public enum OrderWorkKind {
  /** Routed to production planning when sales hands the order over. */
  PLANNING("PLANNING"),
  /** Routed to the warehouse when ship readiness of held stock is asked for. */
  SHIP_READINESS("WAREHOUSE"),
  /** Routed to shipping when the order goes into processing. */
  ARRIVAL_ESTIMATE("SHIPPING"),
  /**
   * Routed to the people who verify delivery details when an order is saved with a consignee,
   * address or note nobody has confirmed (CONSIGNEE-1 C8). It is open while any delivery waits and
   * closes when the last one is verified; one piece of work per order, not per delivery.
   */
  DELIVERY_VERIFICATION("PLANNING");

  private final String defaultDepartment;

  OrderWorkKind(String defaultDepartment) {
    this.defaultDepartment = defaultDepartment;
  }

  /** The system department code the work is routed to unless the tenant routes it elsewhere. */
  public String defaultDepartment() {
    return defaultDepartment;
  }
}
