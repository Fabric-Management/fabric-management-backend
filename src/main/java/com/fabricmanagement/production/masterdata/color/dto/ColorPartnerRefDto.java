package com.fabricmanagement.production.masterdata.color.dto;

import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Partner-to-color relationship with retained primary and alias codes")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ColorPartnerRefDto(
    @Schema(
            description = "Color partner-reference identifier",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
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
            description = "Optional partner-specific Delta-E tolerance override",
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true)
        BigDecimal deltaETolerance,
    @Schema(
            description = "Retained primary and alias codes",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<ColorPartnerCodeDto> codes,
    @Schema(
            description = "Whether the mapping is active",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active,
    @Schema(
            description = "Optimistic-lock version of the aggregate root",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long version) {}
