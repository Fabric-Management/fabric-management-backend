package com.fabricmanagement.product.fiber.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Read-only applicability query: the same effective-composition rules as batch creation. */
@Schema(name = "FiberApplicableQualityStandardsRequest")
public record FiberApplicableQualityStandardsRequest(
    @NotNull
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Product.id of the fibre (the batch productId)")
        UUID productId,
    @Schema(
            nullable = true,
            description =
                "Optional physical composition override keyed by Fiber.id; omit to use the fibre"
                    + " definition. An explicit empty map is invalid")
        Map<UUID, BigDecimal> composition) {}
