package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a StockUnit is transferred between warehouse locations.
 *
 * <p>Listeners: IWM (movement log), WarehouseLocation capacity update.
 */
@Getter
public class StockUnitTransferredEvent extends DomainEvent {

  private final UUID stockUnitId;
  private final String barcode;
  private final UUID batchId;
  private final BigDecimal weight;
  private final String unit;
  private final UUID fromLocationId;
  private final UUID toLocationId;

  public StockUnitTransferredEvent(
      UUID tenantId,
      UUID stockUnitId,
      String barcode,
      UUID batchId,
      BigDecimal weight,
      String unit,
      UUID fromLocationId,
      UUID toLocationId) {
    super(tenantId, "STOCK_UNIT_TRANSFERRED");
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.weight = weight;
    this.unit = unit;
    this.fromLocationId = fromLocationId;
    this.toLocationId = toLocationId;
  }
}
