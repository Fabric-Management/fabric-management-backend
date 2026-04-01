package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when weight is consumed from a StockUnit.
 *
 * <p>Listeners: IWM (stock movement), Batch reconciliation.
 */
@Getter
public class StockUnitConsumedEvent extends DomainEvent {

  private final UUID stockUnitId;
  private final String barcode;
  private final UUID batchId;
  private final BigDecimal consumedAmount;
  private final BigDecimal remainingWeight;
  private final String unit;

  public StockUnitConsumedEvent(
      UUID tenantId,
      UUID stockUnitId,
      String barcode,
      UUID batchId,
      BigDecimal consumedAmount,
      BigDecimal remainingWeight,
      String unit) {
    super(tenantId, "STOCK_UNIT_CONSUMED");
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.consumedAmount = consumedAmount;
    this.remainingWeight = remainingWeight;
    this.unit = unit;
  }
}
