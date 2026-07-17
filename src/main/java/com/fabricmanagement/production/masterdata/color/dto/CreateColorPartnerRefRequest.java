package com.fabricmanagement.production.masterdata.color.dto;

import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Creates a partner-color relationship and its first primary code atomically")
public record CreateColorPartnerRefRequest(
    @Schema(description = "Trading partner identifier", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID partnerId,
    @Schema(
            description = "Business direction in which the partner code is used",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        PartnerRole role,
    @Schema(
            description = "Optional partner-specific Delta-E tolerance override; must be positive",
            nullable = true)
        @Positive
        @DecimalMax("99.99")
        @Digits(integer = 2, fraction = 2)
        BigDecimal deltaETolerance,
    @Schema(
            description = "Required first code, created as the sole active primary",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Valid
        ColorPartnerCodeInput initialPrimaryCode) {}
