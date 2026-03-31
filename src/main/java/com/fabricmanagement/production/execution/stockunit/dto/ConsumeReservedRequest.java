package com.fabricmanagement.production.execution.stockunit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record ConsumeReservedRequest(
    @NotNull @Positive BigDecimal amount, @NotNull UUID workOrderId) {}
