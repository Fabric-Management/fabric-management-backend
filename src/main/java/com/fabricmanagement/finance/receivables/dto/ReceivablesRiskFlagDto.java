package com.fabricmanagement.finance.receivables.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Explainable receivables risk flag")
public record ReceivablesRiskFlagDto(
    @Schema(description = "Risk flag code", example = "SEVERE_OVERDUE") String code,
    @Schema(
            description = "Human-readable reason",
            example = "Customer has receivables 61+ days late")
        String reason) {}
