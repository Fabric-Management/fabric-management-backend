package com.fabricmanagement.finance.invoice.domain;

/**
 * Invoice lifecycle status.
 *
 * <p>Flow: DRAFT -> ISSUED -> SENT -> PARTIALLY_PAID -> PAID
 *
 * <p>Side flows: SENT/PARTIALLY_PAID/OVERDUE -> DISPUTED -> (resolved) SENT
 */
public enum InvoiceStatus {
  DRAFT,
  ISSUED,
  SENT,
  PARTIALLY_PAID,
  PAID,
  OVERDUE,
  DISPUTED,
  CANCELLED,
  VOIDED;

  public boolean isTerminal() {
    return this == PAID || this == CANCELLED || this == VOIDED;
  }

  public boolean canReceivePayment() {
    return this == SENT || this == PARTIALLY_PAID || this == OVERDUE;
  }

  public boolean canCancel() {
    return this == DRAFT || this == ISSUED;
  }

  public boolean isAwaitingPayment() {
    return this == SENT || this == PARTIALLY_PAID || this == OVERDUE;
  }

  public boolean canDispute() {
    return this == SENT || this == PARTIALLY_PAID || this == OVERDUE;
  }

  public boolean canResolveDispute() {
    return this == DISPUTED;
  }

  public boolean canIssue() {
    return this == DRAFT;
  }

  public boolean canSend() {
    return this == ISSUED;
  }

  public boolean canVoid() {
    return !isTerminal();
  }
}
