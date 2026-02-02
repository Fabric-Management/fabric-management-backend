package com.fabricmanagement.finance.invoice.domain;

/**
 * Invoice lifecycle status.
 *
 * <p>Flow: DRAFT → ISSUED → SENT → PARTIALLY_PAID → PAID
 */
public enum InvoiceStatus {
  /** Initial state - invoice being prepared */
  DRAFT,

  /** Invoice finalized and issued */
  ISSUED,

  /** Invoice sent to customer/vendor */
  SENT,

  /** Partial payment received */
  PARTIALLY_PAID,

  /** Fully paid */
  PAID,

  /** Invoice is past due date */
  OVERDUE,

  /** Payment dispute */
  DISPUTED,

  /** Invoice cancelled */
  CANCELLED,

  /** Invoice voided (used for accounting reversal) */
  VOIDED;

  /** Check if invoice is in a terminal state. */
  public boolean isTerminal() {
    return this == PAID || this == CANCELLED || this == VOIDED;
  }

  /** Check if invoice can receive payment. */
  public boolean canReceivePayment() {
    return this == SENT || this == PARTIALLY_PAID || this == OVERDUE;
  }

  /** Check if invoice can be cancelled. */
  public boolean canCancel() {
    return this == DRAFT || this == ISSUED;
  }

  /** Check if invoice is awaiting payment. */
  public boolean isAwaitingPayment() {
    return this == SENT || this == PARTIALLY_PAID || this == OVERDUE;
  }
}
