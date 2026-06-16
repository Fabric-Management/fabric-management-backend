package com.fabricmanagement.finance.receivables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Receivables aging bucket amount in tenant reporting currency")
public record AgingBucketDto(
    @Schema(description = "Bucket code", example = "DAYS_31_60") String bucket,
    @Schema(description = "Bucket amount in reporting currency", example = "12500.0000")
        BigDecimal amount) {}
