package com.fabricmanagement.finance.receivables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Days sales outstanding metric")
public record DsoDto(
    @Schema(description = "Trailing window in days", example = "90") int windowDays,
    @Schema(
            description = "DSO value, or null when there is no sales denominator",
            example = "42.5000")
        BigDecimal daysSalesOutstanding,
    @Schema(description = "Net AR numerator in reporting currency", example = "100000.0000")
        BigDecimal netAccountsReceivable,
    @Schema(description = "Credit sales denominator in reporting currency", example = "211765.0000")
        BigDecimal creditSales,
    @Schema(
            description = "Reason when DSO cannot be computed",
            example = "INSUFFICIENT_SALES_WINDOW")
        String status) {}
