package com.fabricmanagement.production.execution.workorder.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record ConsumeFromStockUnitRequest(
    @NotNull(message = "stockUnitId is required") UUID stockUnitId,
    @NotNull(message = "amount is required") @Positive(message = "amount must be positive")
        BigDecimal amount) {}
