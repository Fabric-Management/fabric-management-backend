package com.fabricmanagement.procurement.rfq.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Fiber-specific RFQ specifications")
public record FiberRFQSpecs(
    @Schema(description = "Required Micronaire value") BigDecimal requiredMicronaire,
    @Schema(description = "Required Staple length in mm") BigDecimal requiredStapleLengthMm,
    @Schema(description = "Required Strength in g/tex") BigDecimal requiredStrength,
    @Schema(description = "Maximum trash content percentage") BigDecimal maxTrashContentPercentage,
    @Schema(description = "Additional fiber quality requirements") String qualityRequirements)
    implements SupplierRFQSpecs {}
