package com.fabricmanagement.finance.period.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EnsureFinancialPeriodRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026") @NotNull @Min(2000)
        Integer year,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "6") @NotNull @Min(1) @Max(12)
        Integer month) {}
