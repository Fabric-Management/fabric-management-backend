package com.fabricmanagement.costing.domain.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateProvider {
  /**
   * Returns the rate for the given currency pair and date. Empty = rate not found — caller decides
   * (prompt user or throw exception).
   */
  Optional<BigDecimal> getRate(
      UUID tenantId, String fromCurrency, String toCurrency, LocalDate date);
}
