package com.fabricmanagement.production.masterdata.color.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "A partner-owned primary or alias code retained by a color mapping")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ColorPartnerCodeDto(
    @Schema(description = "Partner code identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
    @Schema(
            description = "Partner's original trimmed spelling",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String externalCode,
    @Schema(
            description = "Optional partner-owned color name",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        String externalName,
    @Schema(
            description = "Whether this is the relationship's current active primary code",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean primary,
    @Schema(
            description = "Whether this alias can currently participate in lookup",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active) {}
