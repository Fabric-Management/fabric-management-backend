package com.fabricmanagement.finance.cashflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "A single week's cash-flow projection bucket")
public record CashFlowBucketDto(
    @Schema(description = "Bucket key", example = "WEEK_1") String bucketKey,
    @Schema(description = "Human readable label", example = "Overdue / Now") String label,
    @Schema(description = "Start date of the bucket (inclusive)") LocalDate startDate,
    @Schema(description = "End date of the bucket (inclusive)") LocalDate endDate,
    @Schema(description = "Expected inflows (net open AR)") BigDecimal inflows,
    @Schema(description = "Expected outflows (net open AP)") BigDecimal outflows,
    @Schema(description = "Net movement (inflows - outflows)") BigDecimal netMovement,
    @Schema(description = "Cumulative net movement up to this bucket")
        BigDecimal cumulativeNetMovement,
    @Schema(description = "Projected absolute position (only if opening balance was provided)")
        BigDecimal projectedPosition,
    @Schema(description = "True if projected position is negative (cash crunch)")
        boolean cashCrunch,
    @Schema(description = "True if this is the first bucket where a cash crunch occurs")
        boolean firstCrunch) {}
