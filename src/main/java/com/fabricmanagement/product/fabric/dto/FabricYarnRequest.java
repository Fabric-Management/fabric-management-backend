package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.vocabulary.FabricYarnRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** One yarn of a fabric. Within a role, the order of the lines is the yarns' order. */
@Schema(name = "FabricYarnRequest")
public record FabricYarnRequest(
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricYarnRole role,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID yarnProductId,
    @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 2)
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "60",
            description = "Share of the fabric's mass, in percent")
        BigDecimal sharePercent) {}
