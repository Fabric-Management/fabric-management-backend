package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.product.core.domain.ProductType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OpenProductionLotRequest(
    UUID locationId,
    @NotNull(message = "Product type is required") ProductType productType,
    String remarks) {}
