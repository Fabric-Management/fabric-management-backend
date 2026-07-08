package com.fabricmanagement.production.execution.stockunit.dto;

import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateStockUnitApiRequest(
    @NotNull UUID batchId,
    @NotNull ProductType productType,
    @NotBlank @Size(max = 100) String barcode,
    @Size(max = 100) String serialNumber,
    @NotNull PackageType packageType,
    @NotNull @Positive BigDecimal initialWeight,
    BigDecimal grossWeight,
    @NotBlank String unit,
    BigDecimal length,
    @Size(max = 10) String lengthUnit,
    @NotNull UUID locationId,
    @NotNull StockUnitSourceType sourceType,
    UUID sourceId) {}
