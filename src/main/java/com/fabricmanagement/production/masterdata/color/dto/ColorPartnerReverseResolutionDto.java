package com.fabricmanagement.production.masterdata.color.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
    description = "Reverse partner-code lookup result, including alias and current-primary context")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ColorPartnerReverseResolutionDto(
    @Schema(
            description = "Resolved active tenant color card",
            requiredMode = Schema.RequiredMode.REQUIRED)
        ColorDto color,
    @Schema(
            description = "Original display spelling of the alias that matched",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String matchedExternalCode,
    @Schema(
            description = "Optional name stored on the matched alias",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        String matchedExternalName,
    @Schema(
            description = "Whether the matched alias is the current primary",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean matchedIsPrimary,
    @Schema(
            description = "Current active primary code for the relationship",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String primaryExternalCode,
    @Schema(
            description = "Resolved partner-reference identifier for downstream traceability",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID colorPartnerRefId) {}
