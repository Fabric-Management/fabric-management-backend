package com.fabricmanagement.procurement.rfq.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Dyeing and finishing specific RFQ specifications")
public record DyeRFQSpecs(
    @Schema(description = "Required Color code or name") String requiredColor,
    @Schema(description = "Required Dyeing process type") String requiredProcessType,
    @Schema(description = "Required Finishing treatments") String requiredFinish,
    @Schema(description = "Maximum Expected shrinkage after wash") BigDecimal maxShrinkageExpected,
    @Schema(description = "Additional processing requirements") String processRequirements)
    implements SupplierRFQSpecs {}
