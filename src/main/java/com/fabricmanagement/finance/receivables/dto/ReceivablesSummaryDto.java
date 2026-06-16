package com.fabricmanagement.finance.receivables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Receivables intelligence summary")
public record ReceivablesSummaryDto(
    @Schema(description = "As-of date used for aging and FX", example = "2026-06-16")
        LocalDate asOfDate,
    @Schema(description = "Tenant reporting currency", example = "GBP") String reportingCurrency,
    @Schema(
            description = "Net AR in reporting currency after credit-note contra",
            example = "100000.0000")
        BigDecimal totalOutstanding,
    @Schema(description = "Overdue exposure in reporting currency, excluding credit notes")
        BigDecimal overdueExposure,
    @Schema(description = "Overdue exposure as percentage of total net AR", example = "18.7500")
        BigDecimal overdueExposurePercent,
    @Schema(description = "Aging buckets in reporting currency, excluding credit notes")
        List<AgingBucketDto> agingBuckets,
    @Schema(description = "Original-currency breakdown with signed credit-note contra")
        List<ReceivablesCurrencyBreakdownDto> perCurrencyBreakdown,
    @Schema(description = "Top customer concentration")
        List<ReceivablesConcentrationDto> concentration,
    @Schema(description = "Days sales outstanding") DsoDto dso,
    @Schema(description = "Non-fatal data quality or FX warnings")
        List<ReceivablesWarningDto> warnings) {}
