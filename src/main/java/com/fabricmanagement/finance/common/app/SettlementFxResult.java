package com.fabricmanagement.finance.common.app;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SettlementFxResult(
    String reportingCurrency,
    BigDecimal realizedFxGainLoss,
    BigDecimal settlementExchangeRate,
    LocalDate settlementExchangeRateDate) {

  public static SettlementFxResult zero(
      String reportingCurrency, BigDecimal settlementExchangeRate, LocalDate settlementRateDate) {
    return new SettlementFxResult(
        reportingCurrency, BigDecimal.ZERO, settlementExchangeRate, settlementRateDate);
  }
}
