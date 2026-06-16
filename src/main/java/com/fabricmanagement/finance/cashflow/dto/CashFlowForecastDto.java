package com.fabricmanagement.finance.cashflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Cash-flow forecast projection")
public record CashFlowForecastDto(
    @Schema(description = "As-of date the forecast was run") LocalDate asOfDate,
    @Schema(description = "Reporting currency used for consolidation") String reportingCurrency,
    @Schema(
            description = "Forecast mode: NET_MOVEMENT or PROJECTED_POSITION",
            example = "NET_MOVEMENT")
        String mode,
    @Schema(description = "Opening balance provided by the user, if any") BigDecimal openingBalance,
    @Schema(description = "Number of weeks in the horizon") int horizonWeeks,
    @Schema(description = "End date of the horizon (inclusive)") LocalDate horizonEndDate,
    @Schema(description = "Forecast buckets (first is OVERDUE_NOW, followed by weekly buckets)")
        List<CashFlowBucketDto> buckets,
    @Schema(description = "Bucket key of the first cash crunch, if any")
        String firstCrunchBucketKey,
    @Schema(description = "True if a cash crunch occurs in the forecast") boolean hasCrunch,
    @Schema(description = "Non-fatal warnings (e.g. missing FX rates)")
        List<CashFlowWarningDto> warnings) {}
