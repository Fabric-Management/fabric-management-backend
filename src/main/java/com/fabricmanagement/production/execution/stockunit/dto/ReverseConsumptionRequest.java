package com.fabricmanagement.production.execution.stockunit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ReverseConsumptionRequest(
    @NotNull @Positive BigDecimal amount, @NotBlank String reason) {}
