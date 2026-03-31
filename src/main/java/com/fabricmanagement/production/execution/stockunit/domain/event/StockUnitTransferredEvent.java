package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
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
  private final UUID fromLocationId;
  private final UUID toLocationId;

  public StockUnitTransferredEvent(
      UUID tenantId, UUID stockUnitId, String barcode, UUID fromLocationId, UUID toLocationId) {
    super(tenantId, "STOCK_UNIT_TRANSFERRED");
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.fromLocationId = fromLocationId;
    this.toLocationId = toLocationId;
  }
}
