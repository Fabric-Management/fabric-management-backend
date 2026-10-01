package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether the order's delivery term is a proposal or agreed. A term in a draft is a proposal; it
 * becomes agreed when the customer approves the sent order version, or from the start when a prior
 * contract already fixed it. Entering a date never makes a term agreed.
 */
@Schema(name = "DeliveryTermStatus", enumAsRef = true)
public enum DeliveryTermStatus {
  /** Proposed to the customer, not yet accepted. */
  PROPOSED,
  /** Fixed by a prior contract with the customer; the contract reference is kept. */
  AGREED_BY_CONTRACT,
  /** Accepted with the customer's approval of the sent order version; set by the system only. */
  AGREED_BY_CUSTOMER;

  public boolean isAgreed() {
    return this != PROPOSED;
  }
}
