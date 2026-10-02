package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.util.Money;
import java.math.BigDecimal;

/**
 * What one sales order amounts to in one agreed currency, derived from its lines. Amounts are in
 * {@link #currency()} and are never converted: a reporting conversion is a view, not the agreement.
 */
public record OrderCurrencyTotal(
    String currency, BigDecimal subtotal, BigDecimal discount, BigDecimal tax) {

  /** Subtotal less discount. */
  public BigDecimal net() {
    return subtotal.subtract(discount);
  }

  /** Net plus tax. */
  public BigDecimal grandTotal() {
    return net().add(tax);
  }

  public Money netMoney() {
    return Money.of(net(), currency);
  }

  public Money grandTotalMoney() {
    return Money.of(grandTotal(), currency);
  }
}
