package com.fabricmanagement.production.execution.output.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateProductionOutputRequest(
    UUID workOrderId,
    String workOrderNumber,
    UUID batchId,
    @NotNull UUID outputProductId,
    @NotNull ProductType outputProductType,
    String notes) {}
