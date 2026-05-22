package com.fabricmanagement.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.web.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void shouldInitializeCorrectlyWithScale() {
    Money tryMoney = Money.of(new BigDecimal("100.5"), "TRY");
    assertThat(tryMoney.getAmount()).isEqualTo(new BigDecimal("100.50"));
    assertThat(tryMoney.getCurrency().getCurrencyCode()).isEqualTo("TRY");

    Money usdMoney = Money.of(100, "USD");
    assertThat(usdMoney.getAmount()).isEqualTo(new BigDecimal("100.00"));
    assertThat(usdMoney.getCurrency().getCurrencyCode()).isEqualTo("USD");
  }

  @Test
  void shouldRejectNullsInConstructor() {
    assertThatThrownBy(() -> Money.of((BigDecimal) null, "TRY"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Amount cannot be null");

    assertThatThrownBy(() -> Money.of(new BigDecimal("100"), null))
        .isInstanceOf(NullPointerException.class); // Currency.getInstance throws NPE
  }

  @Test
  void shouldImplementEqualsAndHashCode() {
    Money m1 = Money.of(100.50, "TRY");
    Money m2 = Money.of(new BigDecimal("100.50"), "TRY");
    Money m3 = Money.of(100.51, "TRY");
    Money m4 = Money.of(100.50, "USD");

    assertThat(m1).isEqualTo(m2);
    assertThat(m1.hashCode()).isEqualTo(m2.hashCode());

    assertThat(m1).isNotEqualTo(m3);
    assertThat(m1).isNotEqualTo(m4);
  }

  @Test
  void shouldAddSameCurrency() {
    Money m1 = Money.of(100.50, "TRY");
    Money m2 = Money.of(50.25, "TRY");

    Money result = m1.add(m2);

    assertThat(result.getAmount()).isEqualTo(new BigDecimal("150.75"));
    assertThat(result.getCurrency().getCurrencyCode()).isEqualTo("TRY");
  }

  @Test
  void shouldThrowWhenAddingDifferentCurrencies() {
    Money m1 = Money.of(100.50, "TRY");
    Money m2 = Money.of(50.25, "USD");

    assertThatThrownBy(() -> m1.add(m2))
        .isInstanceOf(CurrencyMismatchException.class)
        .hasMessageContaining("Currency mismatch: expected TRY, got USD");
  }

  @Test
  void shouldSubtractSameCurrency() {
    Money m1 = Money.of(100.50, "TRY");
    Money m2 = Money.of(50.25, "TRY");

    Money result = m1.subtract(m2);

    assertThat(result.getAmount()).isEqualTo(new BigDecimal("50.25"));
    assertThat(result.getCurrency().getCurrencyCode()).isEqualTo("TRY");
  }

  @Test
  void shouldMultiplyAndRoundProperly() {
    Money m1 = Money.of(100.55, "TRY");

    // 100.55 * 1.5 = 150.825 -> rounds to 150.83 (HALF_UP)
    Money result = m1.multiply(1.5);

    assertThat(result.getAmount()).isEqualTo(new BigDecimal("150.83"));
    assertThat(result.getCurrency().getCurrencyCode()).isEqualTo("TRY");
  }

  @Test
  void shouldCheckGreaterThanAndLessThan() {
    Money m1 = Money.of(100.50, "TRY");
    Money m2 = Money.of(50.25, "TRY");

    assertThat(m1.isGreaterThan(m2)).isTrue();
    assertThat(m2.isLessThan(m1)).isTrue();
    assertThat(m1.isLessThan(m2)).isFalse();
  }

  @Test
  void shouldCheckPositiveNegativeZero() {
    assertThat(Money.of(100.50, "TRY").isPositive()).isTrue();
    assertThat(Money.of(-100.50, "TRY").isNegative()).isTrue();
    assertThat(Money.zero("TRY").isZero()).isTrue();
  }

  @Test
  void shouldDivideWithDoubleAndRound() {
    Money m = Money.of(100.00, "TRY");
    // 100.00 / 3.0 = 33.333... → rounded to 33.33 (TRY has 2 decimal places)
    Money result = m.divide(3.0);
    assertThat(result.getAmount()).isEqualTo(new BigDecimal("33.33"));
  }

  @Test
  void shouldDivideWithBigDecimal() {
    Money m = Money.of(100.00, "TRY");
    Money result = m.divide(new BigDecimal("3"));
    assertThat(result.getAmount()).isEqualTo(new BigDecimal("33.33"));
  }

  @Test
  void shouldThrowOnDivisionByZero() {
    Money m = Money.of(100.00, "TRY");
    assertThatThrownBy(() -> m.divide(0.0)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  void shouldMultiplyWithBigDecimal() {
    Money m = Money.of(100.55, "TRY");
    Money result = m.multiply(new BigDecimal("1.5"));
    assertThat(result.getAmount()).isEqualTo(new BigDecimal("150.83"));
  }

  @Test
  void shouldNegate() {
    Money m = Money.of(100.50, "TRY");
    Money negated = m.negate();
    assertThat(negated.getAmount()).isEqualTo(new BigDecimal("-100.50"));
    assertThat(negated.getCurrency().getCurrencyCode()).isEqualTo("TRY");
  }

  @Test
  void shouldReturnAbsoluteValue() {
    Money negative = Money.of(-50.25, "TRY");
    Money absolute = negative.abs();
    assertThat(absolute.getAmount()).isEqualTo(new BigDecimal("50.25"));
    assertThat(absolute.isPositive()).isTrue();
  }

  @Test
  void shouldCreateFromLong() {
    Money m = Money.of(100L, "USD");
    assertThat(m.getAmount()).isEqualTo(new BigDecimal("100.00"));
    assertThat(m.getCurrency().getCurrencyCode()).isEqualTo("USD");
  }

  @Test
  void shouldBeEqualRegardlessOfScaleMismatchInInput() {
    Money m1 = Money.of(new BigDecimal("100.5"), "TRY");
    Money m2 = Money.of(new BigDecimal("100.5000"), "TRY");

    // The scale should be normalized by the constructor
    assertThat(m1).isEqualTo(m2);

    // Explicit compareTo == 0 check
    assertThat(m1.getAmount().compareTo(m2.getAmount())).isZero();
  }
}
