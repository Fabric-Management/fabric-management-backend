package com.fabricmanagement.production.execution.output.dto;

import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record AddOutputItemRequest(
    @NotNull PackageType packageType,
    @NotNull @Positive BigDecimal netWeight,
    BigDecimal grossWeight,
    UUID locationId,
    String notes) {}
