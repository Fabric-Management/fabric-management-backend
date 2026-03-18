package com.fabricmanagement.costing.api.dto;

import com.fabricmanagement.costing.domain.currency.ExchangeRateSnapshot;
import com.fabricmanagement.costing.domain.currency.ExchangeRateSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read-only response for an ExchangeRateSnapshot. */
public record ExchangeRateResponse(
    UUID id,
    String baseCurrency,
    String targetCurrency,
    BigDecimal rate,
    ExchangeRateSource source,
    Instant capturedAt) {

  public static ExchangeRateResponse from(ExchangeRateSnapshot snap) {
    return new ExchangeRateResponse(
        snap.getId(),
        snap.getBaseCurrency(),
        snap.getTargetCurrency(),
        snap.getRate(),
        snap.getSource(),
        snap.getCapturedAt());
  }
}
