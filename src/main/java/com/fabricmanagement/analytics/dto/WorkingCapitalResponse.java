package com.fabricmanagement.analytics.dto;

import com.fabricmanagement.finance.payables.dto.DpoDto;
import com.fabricmanagement.finance.receivables.dto.DsoDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Working-capital view: the receivables/payables half of the cash-conversion cycle. DIO and the
 * full CCC (DSO + DIO − DPO) are deferred to INS-7b — surfaced here via {@code dioPending} / {@code
 * cccComplete} flags so the partial figure is never mistaken for the full CCC.
 */
@Schema(
    description =
        "Working capital view (DSO + DPO + operating cash gap). DIO and full CCC pending (INS-7b).")
public record WorkingCapitalResponse(
    @Schema(description = "Days sales outstanding (trailing-90)") DsoDto dso,
    @Schema(description = "Days payable outstanding (trailing-90)") DpoDto dpo,
    @Schema(
            description =
                "Operating cash gap in days = DSO − DPO. EXCLUDES inventory (DIO); not the full CCC."
                    + " Null when DSO or DPO is unavailable.",
            example = "12.5000")
        BigDecimal operatingCashGapDays,
    @Schema(description = "Reporting currency", example = "TRY") String reportingCurrency,
    @Schema(description = "DIO not yet included — always true until INS-7b", example = "true")
        boolean dioPending,
    @Schema(
            description = "Full CCC (DSO + DIO − DPO) computed — always false until INS-7b",
            example = "false")
        boolean cccComplete,
    @Schema(
            description =
                "Warnings (e.g. INSUFFICIENT_SALES_WINDOW / INSUFFICIENT_PURCHASE_WINDOW)")
        List<WorkingCapitalWarningDto> warnings) {}
