package com.fabricmanagement.common.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ConvertedMoneyTest {

  @Test
  void of_ShouldSetAllFieldsCorrectly() {
    BigDecimal originalAmount = new BigDecimal("100.00");
    String originalCurrency = "USD";
    BigDecimal convertedAmount = new BigDecimal("3850.0000");
    String convertedCurrency = "TRY";
    BigDecimal exchangeRate = new BigDecimal("38.50");
    LocalDate rateDate = LocalDate.of(2026, 4, 1);

    ConvertedMoney money =
        ConvertedMoney.of(
            originalAmount,
            originalCurrency,
            convertedAmount,
            convertedCurrency,
            exchangeRate,
            rateDate);

    assertThat(money.getOriginalAmount()).isEqualTo(originalAmount);
    assertThat(money.getOriginalCurrency()).isEqualTo(originalCurrency);
    assertThat(money.getConvertedAmount()).isEqualTo(convertedAmount);
    assertThat(money.getConvertedCurrency()).isEqualTo(convertedCurrency);
    assertThat(money.getExchangeRate()).isEqualTo(exchangeRate);
    assertThat(money.getRateDate()).isEqualTo(rateDate);
  }

  @Test
  void sameUnit_ShouldSetRateToOneAndSameCurrency() {
    BigDecimal amount = new BigDecimal("100.00");
    String currency = "TRY";

    ConvertedMoney money = ConvertedMoney.sameUnit(amount, currency);

    assertThat(money.getOriginalAmount()).isEqualTo(amount);
    assertThat(money.getOriginalCurrency()).isEqualTo(currency);
    assertThat(money.getConvertedAmount()).isEqualTo(amount);
    assertThat(money.getConvertedCurrency()).isEqualTo(currency);
    assertThat(money.getExchangeRate()).isEqualTo(BigDecimal.ONE);
    assertThat(money.getRateDate()).isEqualTo(LocalDate.now());
  }

  @Test
  void equalsAndHashCode_SameValues_ShouldBeEqual() {
    LocalDate date = LocalDate.of(2026, 4, 1);
    ConvertedMoney money1 =
        ConvertedMoney.of(
            new BigDecimal("100.00"),
            "USD",
            new BigDecimal("3850.0000"),
            "TRY",
            new BigDecimal("38.50"),
            date);

    ConvertedMoney money2 =
        ConvertedMoney.of(
            new BigDecimal("100.00"),
            "USD",
            new BigDecimal("3850.0000"),
            "TRY",
            new BigDecimal("38.50"),
            date);

    assertThat(money1).isEqualTo(money2);
    assertThat(money1.hashCode()).isEqualTo(money2.hashCode());
  }

  @Test
  void equalsAndHashCode_DifferentValues_ShouldNotBeEqual() {
    LocalDate date = LocalDate.of(2026, 4, 1);
    ConvertedMoney money1 =
        ConvertedMoney.of(
            new BigDecimal("100.00"),
            "USD",
            new BigDecimal("3850.0000"),
            "TRY",
            new BigDecimal("38.50"),
            date);

    ConvertedMoney money2 =
        ConvertedMoney.of(
            new BigDecimal("200.00"), // Different amount
            "USD",
            new BigDecimal("7700.0000"),
            "TRY",
            new BigDecimal("38.50"),
            date);

    assertThat(money1).isNotEqualTo(money2);
    assertThat(money1.hashCode()).isNotEqualTo(money2.hashCode());
  }
}
