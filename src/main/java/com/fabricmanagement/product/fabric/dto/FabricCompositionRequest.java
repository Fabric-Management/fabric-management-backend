package com.fabricmanagement.product.fabric.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** One fibre of the fabric's declared composition. The fibre decides its ISO code and source. */
@Schema(name = "FabricCompositionRequest")
public record FabricCompositionRequest(
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID fiberId,
    @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 2)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "80")
        BigDecimal percentage) {}
