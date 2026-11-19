package com.fabricmanagement.finance.collection.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Amount-weighted calendar days, with no intermediate rounding. */
public final class CollectionWeightedDate {
  private BigDecimal total = BigDecimal.ZERO;
  private BigDecimal weightedDays = BigDecimal.ZERO;

  public void add(BigDecimal amount, LocalDate date) {
    total = total.add(amount);
    weightedDays = weightedDays.add(amount.multiply(BigDecimal.valueOf(date.toEpochDay())));
  }

  public LocalDate average() {
    if (total.signum() == 0) return null;
    // A half day rounds towards the next calendar day, including dates before 1970.
    long day =
        weightedDays
            .add(total.divide(BigDecimal.valueOf(2)))
            .divide(total, 0, RoundingMode.FLOOR)
            .longValueExact();
    return LocalDate.ofEpochDay(day);
  }
}
