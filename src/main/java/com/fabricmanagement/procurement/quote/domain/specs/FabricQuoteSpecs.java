package com.fabricmanagement.procurement.quote.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Fabric-specific quote specifications")
public record FabricQuoteSpecs(
    @Schema(description = "Fabric construction (e.g., 20x20/60x60)") String construction,
    @Schema(description = "Grams per square meter (GSM)") BigDecimal gsm,
    @Schema(description = "Fabric width in cm") BigDecimal widthCm,
    @Schema(description = "Weave or knit type") String type,
    @Schema(description = "Shrinkage percentage") BigDecimal shrinkagePercentage,
    @Schema(description = "Additional fabric notes") String fabricNotes)
    implements SupplierQuoteSpecs {}
