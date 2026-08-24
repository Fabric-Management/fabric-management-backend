package com.fabricmanagement.production.core.output.dto;

import com.fabricmanagement.production.core.stockunit.domain.PackageType;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProductionOutputItemDto(
    UUID id,
    UUID tenantId,
    String barcode,
    PackageType packageType,
    BigDecimal netWeight,
    BigDecimal grossWeight,
    UUID locationId,
    int sequenceNo,
    String notes) {}
