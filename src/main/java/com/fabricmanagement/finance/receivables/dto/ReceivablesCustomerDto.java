package com.fabricmanagement.finance.receivables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Per-customer receivables rollup")
public record ReceivablesCustomerDto(
    @Schema(description = "Trading partner ID") UUID tradingPartnerId,
    @Schema(description = "Trading partner display name", example = "Acme Textiles")
        String tradingPartnerName,
    @Schema(description = "Net outstanding in reporting currency after credit-note contra")
        BigDecimal outstanding,
    @Schema(description = "Remaining unapplied credit-note balance in reporting currency")
        BigDecimal unappliedCredits,
    @Schema(description = "Overdue exposure in reporting currency, excluding credit notes")
        BigDecimal overdueExposure,
    @Schema(description = "Customer share of total net AR", example = "20.5000")
        BigDecimal concentrationPercent,
    @Schema(description = "Whether any included receivable document is disputed")
        boolean hasDispute,
    @Schema(
            description =
                "Average late days from historical payment allocations, early payments count as zero")
        BigDecimal averageDaysLate,
    @Schema(description = "Aging buckets in reporting currency, excluding credit notes")
        List<AgingBucketDto> agingBuckets,
    @Schema(description = "Original-currency breakdown with signed credit-note contra")
        List<ReceivablesCurrencyBreakdownDto> perCurrencyBreakdown,
    @Schema(description = "Explainable customer risk flags")
        List<ReceivablesRiskFlagDto> riskFlags) {}
