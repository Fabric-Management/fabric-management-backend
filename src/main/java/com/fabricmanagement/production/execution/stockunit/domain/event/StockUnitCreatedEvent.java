package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a new StockUnit is created (e.g. after GoodsReceipt confirmation).
 *
 * <p>Listeners: IWM (stock movement recording), notification hub.
 */
@Getter
public class StockUnitCreatedEvent extends DomainEvent {

  private final UUID stockUnitId;
  private final String barcode;
  private final UUID batchId;
  private final MaterialType materialType;
  private final PackageType packageType;
  private final BigDecimal initialWeight;
  private final String unit;
  private final UUID locationId;

  public StockUnitCreatedEvent(
      UUID tenantId,
      UUID stockUnitId,
      String barcode,
      UUID batchId,
      MaterialType materialType,
      PackageType packageType,
      BigDecimal initialWeight,
      String unit,
      UUID locationId) {
    super(tenantId, "STOCK_UNIT_CREATED");
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.materialType = materialType;
    this.packageType = packageType;
    this.initialWeight = initialWeight;
    this.unit = unit;
    this.locationId = locationId;
  }
}
