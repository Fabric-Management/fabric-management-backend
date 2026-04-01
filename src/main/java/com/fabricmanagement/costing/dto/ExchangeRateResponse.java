package com.fabricmanagement.costing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateResponse(
    String baseCurrency,
    String targetCurrency,
    BigDecimal rate,
    LocalDate rateDate,
    String source) {}
