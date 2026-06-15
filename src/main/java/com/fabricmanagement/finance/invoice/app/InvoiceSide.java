package com.fabricmanagement.finance.invoice.app;

public enum InvoiceSide {
  ACCOUNTS_RECEIVABLE(1),
  ACCOUNTS_PAYABLE(-1);

  private final int realizedFxSign;

  InvoiceSide(int realizedFxSign) {
    this.realizedFxSign = realizedFxSign;
  }

  public int realizedFxSign() {
    return realizedFxSign;
  }
}
