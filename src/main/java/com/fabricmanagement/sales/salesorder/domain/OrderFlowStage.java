package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Where the order stands in sales prepares → planning evaluates → customer approves → processing.
 * It is a separate axis from {@link OrderStatus}: an order with planning or awaiting the customer
 * is still not confirmed. A document sent to the customer titled "draft" does not mean this stage
 * is DRAFT.
 */
@Schema(name = "OrderFlowStage", enumAsRef = true)
public enum OrderFlowStage {
  /** Sales is preparing the order; only here can it be edited. */
  DRAFT,
  /** Sales handed it to planning; waiting for a planner. */
  AWAITING_PLANNING,
  /** A planner is evaluating stock, production, capacity, material and timing. */
  IN_PLANNING,
  /** Planning finished with a current proposal; ready to be sent for the customer's approval. */
  PLANNED,
  /** Sent with an approval link; waiting for the customer's representative. */
  AWAITING_CUSTOMER_APPROVAL,
  /** The customer approved the sent version; its terms are fixed. */
  CUSTOMER_APPROVED;

  private static final Map<OrderFlowStage, Set<OrderFlowStage>> NEXT =
      Map.of(
          DRAFT, EnumSet.of(AWAITING_PLANNING),
          AWAITING_PLANNING, EnumSet.of(IN_PLANNING, DRAFT),
          IN_PLANNING, EnumSet.of(PLANNED, DRAFT),
          PLANNED, EnumSet.of(AWAITING_CUSTOMER_APPROVAL, IN_PLANNING, DRAFT),
          AWAITING_CUSTOMER_APPROVAL, EnumSet.of(CUSTOMER_APPROVED, IN_PLANNING, DRAFT),
          CUSTOMER_APPROVED, EnumSet.noneOf(OrderFlowStage.class));

  /**
   * While planning evaluates the order or the customer is asked to approve it, what was evaluated
   * (products, quantities, tolerances, prices, delivery term, customer requests) does not change;
   * it is changed by taking the order back to the draft. After the customer's approval, changes
   * follow the approved-order rules instead.
   */
  public boolean locksCommercialContent() {
    return this == AWAITING_PLANNING
        || this == IN_PLANNING
        || this == PLANNED
        || this == AWAITING_CUSTOMER_APPROVAL;
  }

  public boolean canMoveTo(OrderFlowStage next) {
    return NEXT.get(this).contains(next);
  }
}
