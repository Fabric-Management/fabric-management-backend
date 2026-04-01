package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a StockUnit reaches zero weight and transitions to DEPLETED.
 *
 * <p>Listeners: Batch reconciliation service, IWM location capacity release.
 */
@Getter
public class StockUnitDepletedEvent extends DomainEvent {

  private final UUID stockUnitId;
  private final String barcode;
  private final UUID batchId;
  private final UUID locationId;

  /** Total weight consumed over the lifetime of this unit (== initialWeight). */
  private final BigDecimal totalConsumedWeight;

  private final String unit;

  public StockUnitDepletedEvent(
      UUID tenantId,
      UUID stockUnitId,
      String barcode,
      UUID batchId,
      UUID locationId,
      BigDecimal totalConsumedWeight,
      String unit) {
    super(tenantId, "STOCK_UNIT_DEPLETED");
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.locationId = locationId;
    this.totalConsumedWeight = totalConsumedWeight;
    this.unit = unit;
  }
}
