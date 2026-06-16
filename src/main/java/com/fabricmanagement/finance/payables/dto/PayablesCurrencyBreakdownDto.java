package com.fabricmanagement.finance.payables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Payables amount by original document currency")
public record PayablesCurrencyBreakdownDto(
    @Schema(description = "ISO-4217 document currency", example = "USD") String currency,
    @Schema(description = "Signed open amount in document currency", example = "1000.0000")
        BigDecimal documentAmount,
    @Schema(
            description = "Signed open amount converted to reporting currency",
            example = "790.0000")
        BigDecimal reportingAmount) {}
