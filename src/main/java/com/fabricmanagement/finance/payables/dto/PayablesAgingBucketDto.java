package com.fabricmanagement.finance.payables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Payables aging bucket amount in tenant reporting currency")
public record PayablesAgingBucketDto(
    @Schema(description = "Bucket code", example = "DAYS_31_60") String bucket,
    @Schema(description = "Bucket amount in reporting currency", example = "12500.0000")
        BigDecimal amount) {}
