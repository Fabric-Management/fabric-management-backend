package com.fabricmanagement.production.execution.stockunit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(name = "StockUnitConsumeRequest")
public record ConsumeRequest(@NotNull @Positive BigDecimal amount) {}
