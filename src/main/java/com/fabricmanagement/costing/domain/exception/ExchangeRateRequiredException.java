package com.fabricmanagement.costing.domain.exception;

import java.time.LocalDate;
import lombok.Getter;

@Getter
public class ExchangeRateRequiredException extends CostingDomainException {

  private final String fromCurrency;
  private final String toCurrency;
  private final LocalDate date;

  public ExchangeRateRequiredException(String from, String to, LocalDate date) {
    super(String.format("Exchange rate required: %s → %s for %s", from, to, date));
    this.fromCurrency = from;
    this.toCurrency = to;
    this.date = date;
  }
}
