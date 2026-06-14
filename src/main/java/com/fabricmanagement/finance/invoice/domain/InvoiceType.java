package com.fabricmanagement.finance.invoice.domain;

/** Type of invoice. */
public enum InvoiceType {
  /** Sales invoice (AR - Accounts Receivable) */
  SALES,

  /** Purchase invoice (AP - Accounts Payable) */
  PURCHASE,

  /** Credit note (customer refund) */
  CREDIT_NOTE,

  /** Debit note (additional charge) */
  DEBIT_NOTE,

  /** Proforma invoice (quotation) */
  PROFORMA;

  /**
   * Whether this type is inherently receivable (AR). CREDIT_NOTE is excluded — its side depends on
   * originalInvoiceId.
   */
  public boolean isReceivable() {
    return this == SALES || this == DEBIT_NOTE;
  }

  /**
   * Whether this type is inherently payable (AP). CREDIT_NOTE is excluded — its side depends on
   * originalInvoiceId.
   */
  public boolean isPayable() {
    return this == PURCHASE;
  }

  /**
   * Whether this type's AR/AP side must be resolved from context (e.g., the original invoice's type
   * for a credit note).
   */
  public boolean isSideDerived() {
    return this == CREDIT_NOTE;
  }

  /** Whether this type can be a target for credit note application or payment allocation. */
  public boolean isSettleable() {
    return this == SALES || this == PURCHASE || this == DEBIT_NOTE;
  }
}
