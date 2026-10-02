package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.util.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderCurrencyTotalsTest {

  @Test
  void eachAgreedCurrencyIsTotalledOnItsOwnLargestGrandTotalFirst() {
    SalesOrderLine euro = priced("100", "2.00", "EUR");
    SalesOrderLine lira = priced("1000", "40.00", "TRY");
    lira.updatePricing(
        "TRY", new BigDecimal("40.00"), new BigDecimal("1000"), new BigDecimal("7800"));
    SalesOrderLine dollar = priced("300", "3.50", "USD");

    OrderCurrencyTotals totals = OrderCurrencyTotals.of(List.of(euro, lira, dollar));

    assertThat(totals.totals())
        .extracting(OrderCurrencyTotal::currency)
        .containsExactly("TRY", "USD", "EUR");
    OrderCurrencyTotal tryTotal = totals.totals().getFirst();
    assertThat(tryTotal.subtotal()).isEqualByComparingTo("40000");
    assertThat(tryTotal.discount()).isEqualByComparingTo("1000");
    assertThat(tryTotal.tax()).isEqualByComparingTo("7800");
    assertThat(tryTotal.net()).isEqualByComparingTo("39000");
    assertThat(tryTotal.grandTotal()).isEqualByComparingTo("46800");
    assertThat(totals.totals().get(1).grandTotal()).isEqualByComparingTo("1050");
    assertThat(totals.unpricedLineCount()).isZero();
  }

  @Test
  void fourDecimalUnitPricesAreTotalledExactlyAndRoundedOncePerCurrency() {
    SalesOrderLine precise = line("1000");
    precise.updatePricing("USD", new BigDecimal("1.2345"), null, null);
    SalesOrderLine thirds = line("3");
    thirds.updatePricing("USD", new BigDecimal("0.3333"), null, null);

    OrderCurrencyTotals totals = OrderCurrencyTotals.of(List.of(precise, thirds));

    // 1234.5 + 0.9999 = 1235.4999 -> 1235.50; a 2-decimal price would have given 1230.99.
    assertThat(totals.totals())
        .singleElement()
        .satisfies(
            total -> {
              assertThat(total.subtotal()).isEqualByComparingTo("1235.50");
              assertThat(total.subtotal().scale()).isEqualTo(2);
            });
  }

  @Test
  void aDiscountThatFitsTheFourDecimalAmountIsAccepted() {
    SalesOrderLine line = line("1000");
    // 1000 x 1.2345 = 1234.50; with a 2-decimal price (1.23) this discount would not fit.
    line.updatePricing("USD", new BigDecimal("1.2345"), new BigDecimal("1234.50"), null);

    assertThat(OrderCurrencyTotals.of(List.of(line)).totals().getFirst().net())
        .isEqualByComparingTo("0");
  }

  @Test
  void anUnpricedLineIsCountedAndNeverReadAsZero() {
    SalesOrderLine unpriced = line("50");
    unpriced.updatePricing("USD", null, null, null);

    OrderCurrencyTotals totals =
        OrderCurrencyTotals.of(List.of(unpriced, priced("10", "4", "USD")));

    assertThat(totals.unpricedLineCount()).isEqualTo(1);
    assertThat(totals.totals())
        .singleElement()
        .satisfies(total -> assertThat(total.grandTotal()).isEqualByComparingTo("40"));
  }

  @Test
  void cancelledAndInactiveLinesDoNotCount() {
    SalesOrderLine cancelled = priced("10", "5", "USD");
    cancelled.setLineStatus(SalesOrderLineStatus.CANCELLED);
    SalesOrderLine removed = priced("10", "5", "USD");
    ReflectionTestUtils.setField(removed, "isActive", false);

    OrderCurrencyTotals totals =
        OrderCurrencyTotals.of(List.of(cancelled, removed, priced("2", "5", "USD")));

    assertThat(totals.totals())
        .singleElement()
        .satisfies(total -> assertThat(total.subtotal()).isEqualByComparingTo("10"));
  }

  private static SalesOrderLine priced(String quantity, String price, String currency) {
    SalesOrderLine line = line(quantity);
    line.updateUnitPrice(Money.of(new BigDecimal(price), currency));
    return line;
  }

  private static SalesOrderLine line(String quantity) {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal(quantity))
            .unit("M")
            .lineStatus(SalesOrderLineStatus.PENDING)
            .build();
    ReflectionTestUtils.setField(line, "isActive", true);
    return line;
  }
}
