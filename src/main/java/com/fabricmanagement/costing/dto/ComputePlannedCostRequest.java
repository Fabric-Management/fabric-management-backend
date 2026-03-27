package com.fabricmanagement.costing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Request DTO for computing a PLANNED (WorkOrder) cost. */
public record ComputePlannedCostRequest(
    @NotNull UUID workOrderId,
    @NotBlank String moduleType,
    @NotNull UUID materialId,
    @NotNull @Positive BigDecimal plannedQuantityKg,
    UUID supplierId) {}
