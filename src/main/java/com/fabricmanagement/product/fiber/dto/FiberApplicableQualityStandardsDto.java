package com.fabricmanagement.product.fiber.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Every active tenant profile applicable to the exact input, plus the resolver's default. Nothing
 * is persisted by the query.
 */
@Schema(name = "FiberApplicableQualityStandardsDto")
public record FiberApplicableQualityStandardsDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FiberQualityStandardDto> profiles,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Profile the batch would use when none is chosen; null when none applies")
        UUID defaultProfileId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        FiberQualityResolutionReason resolutionReason) {}
