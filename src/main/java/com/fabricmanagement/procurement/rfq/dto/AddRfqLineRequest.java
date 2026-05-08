package com.fabricmanagement.procurement.rfq.dto;

import com.fabricmanagement.procurement.rfq.domain.specs.SupplierRFQSpecs;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Fix #2 — İstemciden ham entity değil, bu DTO alınmalı. */
@Schema(description = "Request to add a line to an RFQ")
public record AddRfqLineRequest(
    @Schema(description = "Material ID") UUID materialId,
    @Schema(description = "Product description") String productDesc,
    @NotNull(message = "Requested quantity is required")
        @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
        @Schema(description = "Requested quantity", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal requestedQty,
    @NotBlank(message = "Unit is required")
        @Schema(description = "Unit of measure", requiredMode = Schema.RequiredMode.REQUIRED)
        String unit,
    @Schema(description = "Module-specific requirements") SupplierRFQSpecs moduleSpecs) {}
