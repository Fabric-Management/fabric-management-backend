package com.fabricmanagement.procurement.quote.dto;

import com.fabricmanagement.procurement.quote.domain.specs.SupplierQuoteSpecs;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Request to add a line to a supplier quote")
public record AddQuoteLineRequest(
    @NotNull(message = "RFQ line ID is required")
        @Schema(description = "RFQ Line ID", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID rfqLineId,
    @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0001", message = "Unit price must be positive")
        @Schema(description = "Unit price", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal unitPrice,
    @NotBlank(message = "Currency is required")
        @Schema(
            description = "Currency code",
            example = "TRY",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,
    @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
        @Schema(description = "Quantity", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal qty,
    @NotBlank(message = "Unit is required")
        @Schema(description = "Unit of measure", requiredMode = Schema.RequiredMode.REQUIRED)
        String unit,
    @Schema(description = "Volume discounts") Map<String, Object> volumeDiscounts,
    @Schema(description = "Module-specific specifications") SupplierQuoteSpecs moduleSpecs,
    @Schema(description = "Line notes") String notes) {}
