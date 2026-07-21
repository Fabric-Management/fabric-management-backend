package com.fabricmanagement.production.quality.decision.dto;

import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

public record QualityDecisionUnitDto(
    UUID id,
    String barcode,
    PackageType packageType,
    BigDecimal currentWeight,
    String unit,
    BigDecimal length,
    String lengthUnit,
    StockUnitStatus status,
    QualityDisposition qualityDisposition,
    @Schema(
            description = "Whether the unit status is eligible for a quality decision",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean unitStatusEligible) {}
