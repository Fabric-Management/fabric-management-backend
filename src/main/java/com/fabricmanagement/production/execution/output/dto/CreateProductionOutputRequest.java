package com.fabricmanagement.production.execution.output.dto;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateProductionOutputRequest(
    UUID workOrderId,
    String workOrderNumber,
    UUID batchId,
    @NotNull UUID outputMaterialId,
    @NotNull MaterialType outputMaterialType,
    String notes) {}
