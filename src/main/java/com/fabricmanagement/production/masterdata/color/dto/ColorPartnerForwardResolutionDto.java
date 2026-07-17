package com.fabricmanagement.production.masterdata.color.dto;

import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Forward lookup result containing the active primary partner code")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ColorPartnerForwardResolutionDto(
    @Schema(
            description = "Resolved partner-reference identifier",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID colorPartnerRefId,
    @Schema(
            description = "Tenant-owned color identifier",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID colorId,
    @Schema(description = "Trading partner identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID partnerId,
    @Schema(
            description = "Business direction of the mapping",
            requiredMode = Schema.RequiredMode.REQUIRED)
        PartnerRole role,
    @Schema(
            description = "Partner's active primary code in its original spelling",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String externalCode,
    @Schema(
            description = "Optional name stored on the primary code",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        String externalName) {}
