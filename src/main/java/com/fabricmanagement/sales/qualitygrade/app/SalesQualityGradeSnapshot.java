package com.fabricmanagement.sales.qualitygrade.app;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesQualityGradeSnapshot(
    UUID id, String code, String name, BigDecimal priceFactor) {}
