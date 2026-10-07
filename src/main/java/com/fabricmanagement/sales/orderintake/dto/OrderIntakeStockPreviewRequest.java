package com.fabricmanagement.sales.orderintake.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * An order line that is not saved yet, as the order form holds it (STOCK-PREVIEW-1). The
 * constraints and messages are those of a saved line ({@code SalesOrderLineRequest}), so a preview
 * accepts what a line accepts. Tolerances are on the percent scale the line stores (5 means 5%).
 */
@Schema(name = "OrderIntakeStockPreviewRequest")
public record OrderIntakeStockPreviewRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "Product is required")
        UUID productId,
    @Schema(description = "Colour card; omit for colourless stock") UUID colorId,
    @Schema(description = "Finished width; must be one of the product's defined widths")
        @DecimalMin(value = "0.01", message = "Finished width must be positive")
        BigDecimal finishedWidth,
    @Schema(
            description = "Unit of the finished width",
            allowableValues = {"CM", "IN"})
        @Pattern(regexp = "CM|IN|cm|in", message = "Width unit must be CM or IN")
        String finishedWidthUnit,
    @Schema(description = "Customer, so its confirmed lot compatibility is honoured")
        UUID customerId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Requested quantity is required")
        @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
        BigDecimal requestedQty,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "Unit is required")
        String unit,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The customer requires this line from a single dye lot")
        @NotNull(message = "Single-lot requirement is required")
        Boolean singleLotRequired,
    @Schema(
            description =
                "Quantity tolerance above the requested quantity agreed for this line (%)")
        @DecimalMin(value = "0", message = "A tolerance cannot be negative")
        @DecimalMax(value = "100", message = "A tolerance cannot exceed 100 percent")
        BigDecimal toleranceUpPct,
    @Schema(
            description =
                "Quantity tolerance below the requested quantity agreed for this line (%)")
        @DecimalMin(value = "0", message = "A tolerance cannot be negative")
        @DecimalMax(value = "100", message = "A tolerance cannot exceed 100 percent")
        BigDecimal toleranceDownPct) {}
