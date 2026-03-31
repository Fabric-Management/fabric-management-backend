package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a StockUnit is disposed (scrap, destruction, write-off). Terminal event.
 *
 * <p>Listeners: IWM (stock write-off movement), finance (potential cost recording), audit trail.
 */
@Getter
public class StockUnitDisposedEvent extends DomainEvent {

  private final UUID stockUnitId;
  private final String barcode;
  private final UUID batchId;
  private final UUID locationId;

  /** Remaining weight at the time of disposal. */
  private final BigDecimal disposedWeight;

  private final String unit;

  /** Mandatory reason provided by the admin at time of disposal. */
  private final String reason;

  public StockUnitDisposedEvent(
      UUID tenantId,
      UUID stockUnitId,
      String barcode,
      UUID batchId,
      UUID locationId,
      BigDecimal disposedWeight,
      String unit,
      String reason) {
    super(tenantId, "STOCK_UNIT_DISPOSED");
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.locationId = locationId;
    this.disposedWeight = disposedWeight;
    this.unit = unit;
    this.reason = reason;
  }
}
