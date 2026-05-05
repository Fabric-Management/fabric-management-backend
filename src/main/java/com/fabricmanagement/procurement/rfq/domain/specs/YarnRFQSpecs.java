package com.fabricmanagement.procurement.rfq.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Yarn-specific RFQ specifications")
public record YarnRFQSpecs(
    @Schema(description = "Required Yarn count (e.g., Ne 30/1)") String requiredYarnCount,
    @Schema(description = "Required Blend composition (e.g., 100% Cotton)") String requiredBlend,
    @Schema(description = "Required Twist level (TPM or TPI)") BigDecimal requiredTwist,
    @Schema(description = "Required Strength (RKM)") BigDecimal requiredStrength,
    @Schema(description = "Additional yarn requirements") String yarnRequirements)
    implements SupplierRFQSpecs {}
