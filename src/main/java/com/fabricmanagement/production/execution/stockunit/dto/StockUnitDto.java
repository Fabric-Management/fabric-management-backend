package com.fabricmanagement.production.execution.stockunit.dto;

import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockUnitDto(
    UUID id,
    Instant createdAt,
    Instant updatedAt,
    UUID tenantId,
    String barcode,
    String serialNumber,
    UUID batchId,
    PackageType packageType,
    ProductType productType,
    BigDecimal initialWeight,
    BigDecimal currentWeight,
    BigDecimal consumedWeight,
    BigDecimal consumptionPercent,
    BigDecimal grossWeight,
    String unit,
    UUID locationId,
    UUID previousLocationId,
    UUID qualityGradeId,
    UUID previousGradeId,
    StockUnitStatus status,
    StockUnitSourceType sourceType,
    UUID sourceId,
    boolean flagged,
    String flagReason,
    String flagDetails) {

  public static StockUnitDto from(StockUnit entity) {
    if (entity == null) {
      return null;
    }
    return new StockUnitDto(
        entity.getId(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getTenantId(),
        entity.getBarcode(),
        entity.getSerialNumber(),
        entity.getBatchId(),
        entity.getPackageType(),
        entity.getProductType(),
        entity.getInitialWeight(),
        entity.getCurrentWeight(),
        entity.getConsumedWeight(),
        entity.getConsumptionPercent(),
        entity.getGrossWeight(),
        entity.getUnit(),
        entity.getLocationId(),
        entity.getPreviousLocationId(),
        entity.getQualityGradeId(),
        entity.getPreviousGradeId(),
        entity.getStatus(),
        entity.getSourceType(),
        entity.getSourceId(),
        entity.isFlagged(),
        entity.getFlagReason(),
        entity.getFlagDetails());
  }
}
