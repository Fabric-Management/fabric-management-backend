package com.fabricmanagement.sales.orderintake.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stock a product has in one colour, as the order form shows it while a line is being entered. Read
 * from the same piece-level proposal source that quantity evaluation (SOI D2) uses, so the number
 * the form shows is the number the evaluation will later work from. Nothing is reserved; the
 * quantity is advisory and the colour stays selectable whatever it says.
 */
@Schema(name = "OrderIntakeAvailability")
public record OrderIntakeAvailabilityDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Colour card the stock was matched on; null means colourless stock only")
        UUID colorId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Canonical unit of the quantities (M for fabric, KG for yarn and fibre)")
        String unit,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Sum of whole pieces that may be offered now, in the canonical unit")
        BigDecimal availableQuantity,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Sum of pieces whose availability is not yet known (missing evidence, anonymous"
                    + " reservation), in the canonical unit; never counted as available")
        BigDecimal unknownQuantity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long lotCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long availablePieceCount,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Pieces whose availability is not yet known, measured or not")
        long unknownPieceCount,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Pieces with no exact canonical measure; counted here, never in a quantity")
        long unmeasuredPieceCount) {}
