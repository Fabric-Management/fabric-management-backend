package com.fabricmanagement.finance.payables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Days payable outstanding metric")
public record DpoDto(
    @Schema(description = "Trailing window in days", example = "90") int windowDays,
    @Schema(
            description = "DPO value, or null when there is no purchases denominator",
            example = "42.5000")
        BigDecimal daysPayableOutstanding,
    @Schema(description = "Net AP numerator in reporting currency", example = "100000.0000")
        BigDecimal netAccountsPayable,
    @Schema(description = "Purchases denominator in reporting currency", example = "211765.0000")
        BigDecimal purchases,
    @Schema(
            description = "Reason when DPO cannot be computed",
            example = "INSUFFICIENT_PURCHASE_WINDOW")
        String status) {}
