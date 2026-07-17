package com.fabricmanagement.production.masterdata.color.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Full mutable state of a partner-color relationship")
public record UpdateColorPartnerRefRequest(
    @Schema(
            description = "Optional partner-specific Delta-E tolerance override; null clears it",
            nullable = true)
        @Positive
        @DecimalMax("99.99")
        @Digits(integer = 2, fraction = 2)
        BigDecimal deltaETolerance) {}
