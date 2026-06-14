package com.fabricmanagement.costing.domain.calculation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Immutable record stored as a JSONB array element in CostCalculation.missingItems.
 *
 * <p>Each entry documents one cost item that was skipped during calculation, typically due to
 * missing price data.
 */
@Schema(description = "A cost item that was skipped during calculation due to missing data")
public record MissingCostItemEntry(
    @Schema(description = "The cost item code that was skipped", example = "RAW_PRODUCT")
        String costItemCode,
    @Schema(
            description = "Product ID if applicable (e.g. for per-product RAW_PRODUCT lookups)",
            nullable = true)
        UUID productId,
    @Schema(
            description = "Human-readable reason for the skip",
            example = "No price found in price list")
        String reason) {}
