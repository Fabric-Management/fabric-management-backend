package com.fabricmanagement.costing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Request DTO for computing an ESTIMATED (Quote) cost. */
public record ComputeEstimatedCostRequest(
    @NotNull UUID quoteId,
    @NotBlank String moduleType,
    @NotNull UUID materialId,
    @NotNull @Positive BigDecimal totalQuantityKg,
    UUID tradingPartnerId) {}
