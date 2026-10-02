package com.fabricmanagement.product.fiber.dto;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One resolved composition component (FIBER-CATALOG-1). A pure fibre returns exactly one component
 * at 100%; a blend returns every component, so clients never look components up again.
 */
@Schema(name = "FiberCompositionComponentDto")
public record FiberCompositionComponentDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Component Fiber.id")
        UUID fiberId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Shared ISO code row id")
        UUID isoCodeId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Shared ISO code, e.g. PES")
        String isoCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fiberName,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Exact decimal percentage; never rounded")
        BigDecimal percentage,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Declared source of this component; null means undeclared")
        MaterialSource materialSource) {}
