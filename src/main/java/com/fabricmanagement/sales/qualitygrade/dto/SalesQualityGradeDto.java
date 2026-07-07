package com.fabricmanagement.sales.qualitygrade.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesQualityGradeDto(
    UUID id, String code, String name, BigDecimal priceFactor, boolean saleable) {}
