package com.fabricmanagement.procurement.rfq.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Fabric-specific RFQ specifications")
public record FabricRFQSpecs(
    @Schema(description = "Required Fabric construction (e.g., 20x20/60x60)")
        String requiredConstruction,
    @Schema(description = "Required Grams per square meter (GSM)") BigDecimal requiredGsm,
    @Schema(description = "Required Fabric width in cm") BigDecimal requiredWidthCm,
    @Schema(description = "Required Weave or knit type") String requiredType,
    @Schema(description = "Maximum Shrinkage percentage") BigDecimal maxShrinkagePercentage,
    @Schema(description = "Additional fabric requirements") String fabricRequirements)
    implements SupplierRFQSpecs {}
