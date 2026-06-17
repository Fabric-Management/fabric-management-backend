package com.fabricmanagement.finance.invoice.domain;

/**
 * Invoice document lifecycle status.
 *
 * <p>Flow: DRAFT -> ISSUED -> SENT -> (terminal)
 *
 * <p>Side flows: SENT -> DISPUTED -> (resolved) SENT
 */
public enum InvoiceStatus {
  DRAFT,
  ISSUED,
  SENT,
  DISPUTED,
  CANCELLED,
  VOIDED;

  /**
   * Invoice statuses that are excluded when calculating recognized/issued revenue. DRAFT represents
   * uninvoiced work; CANCELLED and VOIDED represent nullified invoices.
   */
  public static final java.util.List<InvoiceStatus> EXCLUDED_FROM_ISSUED_REVENUE =
      java.util.List.of(CANCELLED, VOIDED, DRAFT);

  public boolean isTerminal() {
    return this == CANCELLED || this == VOIDED;
  }

  public boolean canReceivePayment() {
    return this == SENT;
  }

  public boolean canCancel() {
    return this == DRAFT || this == ISSUED;
  }

  public boolean canDispute() {
    return this == SENT;
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
