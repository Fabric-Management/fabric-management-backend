package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Where one request for the customer's approval of a sent version stands. */
@Schema(name = "CustomerApprovalStatus", enumAsRef = true)
public enum CustomerApprovalStatus {
  /** The version waits for a manager's or finance approval; nothing was sent yet. */
  AWAITING_INTERNAL_APPROVAL,
  /** The manager or finance declined it; it is never sent. */
  INTERNAL_REJECTED,
  /** Sent to the customer; waits for the representative's decision until the link expires. */
  SENT,
  /** The customer approved the version and the order was confirmed. */
  APPROVED,
  /**
   * The customer approved, but stock or the plan no longer met the approved terms: the order was
   * not confirmed and went back to planning.
   */
  APPROVED_NOT_FULFILLABLE,
  /** The customer asked for changes with a note; the order went back to sales. */
  CHANGES_REQUESTED,
  /**
   * Taken back before a decision (reopened, withdrawn, resent or cancelled); kept for the record.
   */
  WITHDRAWN;

  /** No further decision can be taken on it. */
  public boolean isClosed() {
    return this != AWAITING_INTERNAL_APPROVAL && this != SENT;
  }

  /** The customer decided on it. */
  public boolean isDecidedByCustomer() {
    return this == APPROVED || this == APPROVED_NOT_FULFILLABLE || this == CHANGES_REQUESTED;
  }
}
