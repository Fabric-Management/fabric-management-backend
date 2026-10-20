package com.fabricmanagement.product.fabric.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "FabricWoven", description = "Nominal woven design values")
public record FabricWovenDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "52.0")
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal endsPerCm,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "28.0")
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal picksPerCm,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Size(max = 40)
        String reedNotation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "3/1 S")
        @Size(max = 40)
        String weaveNotation) {}
