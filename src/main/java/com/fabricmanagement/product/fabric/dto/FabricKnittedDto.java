package com.fabricmanagement.product.fabric.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(name = "FabricKnitted", description = "Nominal knitting machine and stitch values")
public record FabricKnittedDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "28") @Positive
        Integer machineGauge,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "30.0")
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal machineDiameterIn,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal coursesPerCm,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal walesPerCm,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2.750")
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal stitchLengthMm,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Positive
        Integer feeders) {}
