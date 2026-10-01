package com.fabricmanagement.sales.salesorder.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-currency totals of a sales order, derived from its lines on every read so they cannot drift
 * from them. Only active, not-cancelled lines count. A line without an agreed price adds nothing
 * and is counted in {@link #unpricedLineCount()}, so a missing price is never read as zero.
 * Currencies are ordered by grand total, largest first (nominal amounts, no conversion).
 *
 * <p>Line amounts are summed from the stored amounts (unit prices keep four decimals) and rounded
 * once per currency to its minor unit, so totals match what the line validation checked.
 */
public record OrderCurrencyTotals(List<OrderCurrencyTotal> totals, int unpricedLineCount) {

  public static final OrderCurrencyTotals EMPTY = new OrderCurrencyTotals(List.of(), 0);

  public OrderCurrencyTotals {
    totals = List.copyOf(totals);
  }

  public static OrderCurrencyTotals of(Collection<SalesOrderLine> lines) {
    Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
    int unpriced = 0;
    for (SalesOrderLine line : lines) {
      if (!Boolean.TRUE.equals(line.getIsActive())
          || line.getLineStatus() == SalesOrderLineStatus.CANCELLED) {
        continue;
      }
      if (line.getUnitPriceAmount() == null || line.getRequestedQty() == null) {
        unpriced++;
        continue;
      }
      BigDecimal[] sum =
          sums.computeIfAbsent(
              line.getCurrency(),
              currency -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
      sum[0] = sum[0].add(line.getUnitPriceAmount().multiply(line.getRequestedQty()));
      if (line.getDiscountAmountValue() != null) {
        sum[1] = sum[1].add(line.getDiscountAmountValue());
      }
      if (line.getTaxAmountValue() != null) {
        sum[2] = sum[2].add(line.getTaxAmountValue());
      }
    }
    List<OrderCurrencyTotal> totals =
        sums.entrySet().stream()
            .map(
                entry ->
                    new OrderCurrencyTotal(
                        entry.getKey(),
                        minorUnits(entry.getKey(), entry.getValue()[0]),
                        minorUnits(entry.getKey(), entry.getValue()[1]),
                        minorUnits(entry.getKey(), entry.getValue()[2])))
            .sorted(
                Comparator.comparing(OrderCurrencyTotal::grandTotal)
                    .reversed()
                    .thenComparing(OrderCurrencyTotal::currency))
            .toList();
    return new OrderCurrencyTotals(totals, unpriced);
  }

  private static BigDecimal minorUnits(String currency, BigDecimal amount) {
    int digits;
    try {
      digits = java.util.Currency.getInstance(currency).getDefaultFractionDigits();
    } catch (IllegalArgumentException unknown) {
      digits = 2;
    }
    return amount.setScale(Math.max(digits, 0), RoundingMode.HALF_UP);
  }
}
