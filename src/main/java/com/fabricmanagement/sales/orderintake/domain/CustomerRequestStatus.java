package com.fabricmanagement.sales.orderintake.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Life of a customer's custom product request (SOI §6 custom-request lifecycle, R16–R18). */
@Schema(name = "CustomerRequestStatus", enumAsRef = true)
public enum CustomerRequestStatus {
  /** Recorded; waits for a technical evaluation. */
  OPEN,
  /** The evaluator needs more information from the customer. */
  NEEDS_INFO,
  /** The evaluator found no feasible solution; the salesperson closes or re-opens it. */
  NOT_FEASIBLE,
  /** A solution revision is ready to be presented. */
  PROPOSAL_READY,
  SENT_TO_CUSTOMER,
  CUSTOMER_APPROVED,
  CUSTOMER_REJECTED,
  /** Turned into an order line of the approved product. */
  RESOLVED,
  CLOSED;

  public boolean isFinished() {
    return this == RESOLVED || this == CLOSED;
  }
}
