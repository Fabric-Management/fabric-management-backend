package com.fabricmanagement.costing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Request DTO for computing an ACTUAL (Batch) cost. */
public record ComputeActualCostRequest(
    @NotNull UUID batchId,
    @NotBlank String moduleType,
    @NotNull UUID productId,
    @NotNull @Positive BigDecimal actualQuantityKg,
    UUID supplierId) {}
