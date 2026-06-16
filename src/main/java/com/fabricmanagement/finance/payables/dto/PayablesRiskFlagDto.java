package com.fabricmanagement.finance.payables.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Explainable payables risk flag")
public record PayablesRiskFlagDto(
    @Schema(description = "Risk flag code", example = "LARGE_UPCOMING_OUTFLOW") String code,
    @Schema(
            description = "Human-readable reason",
            example =
                "Supplier has an invoice due within 7 days whose reporting amount is ≥ 25% of total net AP")
        String reason) {}
