package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public final class CollectionAmounts {
  private CollectionAmounts() {}

  public static BigDecimal positive(BigDecimal amount, String currency) {
    if (amount == null || amount.signum() <= 0)
      throw CollectionRuleException.invalid("Amount must be positive");
    return exact(amount, currency);
  }

  public static BigDecimal exact(BigDecimal amount, String currency) {
    try {
      int scale = Currency.getInstance(currency).getDefaultFractionDigits();
      if (scale < 0 || scale > 4 || amount == null || amount.signum() < 0)
        throw new IllegalArgumentException();
      BigDecimal result = amount.setScale(scale, RoundingMode.UNNECESSARY);
      if (result.precision() - result.scale() > 15) throw new IllegalArgumentException();
      return result;
    } catch (IllegalArgumentException | ArithmeticException ex) {
      throw CollectionRuleException.invalid("Amount or currency precision is invalid");
    }
  }

  /**
   * An amount as API text at its currency's scale. Stored amounts carry the column's scale (4), but
   * every amount here is exact at the currency's scale ({@link #exact}), so nothing is lost.
   */
  public static String text(BigDecimal amount, String currency) {
    int scale = Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
    return amount.setScale(scale, RoundingMode.HALF_UP).toPlainString();
  }

  public static BigDecimal outstanding(BigDecimal expected, BigDecimal settled) {
    return expected.subtract(settled).max(BigDecimal.ZERO);
  }

  /** Cash recorded above what is agreed; all of it once the expectation is cancelled. */
  public static BigDecimal surplus(BigDecimal expected, boolean cancelled, BigDecimal settled) {
    return (cancelled ? settled : settled.subtract(expected)).max(BigDecimal.ZERO);
  }
}
