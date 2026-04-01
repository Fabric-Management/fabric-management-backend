package com.fabricmanagement.production.execution.stockunit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ConsumeRequest(@NotNull @Positive BigDecimal amount) {}
