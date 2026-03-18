package com.fabricmanagement.costing.api.dto;

import com.fabricmanagement.costing.domain.currency.ExchangeRateSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Request DTO for capturing an exchange rate snapshot. */
public record CaptureExchangeRateRequest(
    @NotBlank String baseCurrency,
    @NotBlank String targetCurrency,
    @NotNull @Positive BigDecimal rate,
    @NotNull ExchangeRateSource source) {}
