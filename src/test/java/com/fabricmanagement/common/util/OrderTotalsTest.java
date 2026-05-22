package com.fabricmanagement.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.web.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderTotalsTest {

  @Test
  void shouldCreateZeroTotals() {
    OrderTotals totals = OrderTotals.zero("TRY");

    assertThat(totals.getCurrency()).isEqualTo("TRY");
    assertThat(totals.getTotalAmount().isZero()).isTrue();
    assertThat(totals.getTaxAmount().isZero()).isTrue();
    assertThat(totals.getDiscountAmount().isZero()).isTrue();
    assertThat(totals.calculateGrandTotal().isZero()).isTrue();
  }

  @Test
  void shouldUpdateAmountsWithSameCurrency() {
    OrderTotals totals =
        OrderTotals.zero("TRY")
            .withTotalAmount(Money.of(100.0, "TRY"))
            .withTaxAmount(Money.of(18.0, "TRY"))
            .withDiscountAmount(Money.of(10.0, "TRY"));

    assertThat(totals.getTotalAmount().getAmount()).isEqualTo(new BigDecimal("100.00"));
    assertThat(totals.getTaxAmount().getAmount()).isEqualTo(new BigDecimal("18.00"));
    assertThat(totals.getDiscountAmount().getAmount()).isEqualTo(new BigDecimal("10.00"));
  }

  @Test
  void shouldCalculateGrandTotalCorrectly() {
    OrderTotals totals =
        OrderTotals.zero("USD")
            .withTotalAmount(Money.of(100.50, "USD"))
            .withTaxAmount(Money.of(20.00, "USD"))
            .withDiscountAmount(Money.of(5.25, "USD"));

    Money grandTotal = totals.calculateGrandTotal();

    assertThat(grandTotal.getAmount()).isEqualTo(new BigDecimal("115.25"));
    assertThat(grandTotal.getCurrency().getCurrencyCode()).isEqualTo("USD");
  }

  @Test
  void shouldThrowWhenUpdatingWithDifferentCurrency() {
    OrderTotals totals = OrderTotals.zero("TRY");

    assertThatThrownBy(() -> totals.withTotalAmount(Money.of(100, "USD")))
        .isInstanceOf(CurrencyMismatchException.class)
        .hasMessageContaining("Currency mismatch: expected TRY, got USD");

    assertThatThrownBy(() -> totals.withTaxAmount(Money.of(18, "EUR")))
        .isInstanceOf(CurrencyMismatchException.class);

    assertThatThrownBy(() -> totals.withDiscountAmount(Money.of(10, "GBP")))
        .isInstanceOf(CurrencyMismatchException.class);
  }

  @Test
  void shouldHandleNullAmountsInGrandTotal() {
    // Simulate partial updates where taxAmount and discountAmount remain at zero defaults
    OrderTotals totals = OrderTotals.zero("TRY").withTotalAmount(Money.of(100.0, "TRY"));

    // taxAmount and discountAmount are still zero from zero() factory
    Money grandTotal = totals.calculateGrandTotal();

    assertThat(grandTotal.getAmount()).isEqualByComparingTo("100.00");
  }
}
