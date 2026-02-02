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

  /** Check if this is an AR (receivable) type. */
  public boolean isReceivable() {
    return this == SALES || this == DEBIT_NOTE;
  }

  /** Check if this is an AP (payable) type. */
  public boolean isPayable() {
    return this == PURCHASE || this == CREDIT_NOTE;
  }
}
