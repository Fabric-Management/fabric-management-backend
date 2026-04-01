package com.fabricmanagement.costing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateSubmitRequest(
    @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be ISO 4217 (3 uppercase letters)")
        String baseCurrency,
    @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be ISO 4217 (3 uppercase letters)")
        String targetCurrency,
    @NotNull @Positive BigDecimal rate,
    LocalDate rateDate) {}
