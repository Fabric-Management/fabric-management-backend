package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

@Getter
public class StockUnitSoftHoldPlacedEvent extends DomainEvent {
  private final UUID quoteLineId;
  private final UUID stockUnitId;
  private final UUID softHoldId;

  public StockUnitSoftHoldPlacedEvent(
      UUID tenantId, UUID quoteLineId, UUID stockUnitId, UUID softHoldId) {
    super(tenantId, "STOCK_UNIT_SOFT_HOLD_PLACED");
    this.quoteLineId = quoteLineId;
    this.stockUnitId = stockUnitId;
    this.softHoldId = softHoldId;
  }
}
