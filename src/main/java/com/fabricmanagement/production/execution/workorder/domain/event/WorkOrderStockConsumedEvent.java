package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/** Published when a StockUnit is physically consumed for a WorkOrder on the shop floor. */
@Getter
public class WorkOrderStockConsumedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final UUID stockUnitId;
  private final UUID batchId;
  private final BigDecimal consumedWeight;
  private final String unit;

  public WorkOrderStockConsumedEvent(
      UUID tenantId,
      UUID workOrderId,
      UUID stockUnitId,
      UUID batchId,
      BigDecimal consumedWeight,
      String unit) {
    super(tenantId, "WORK_ORDER_STOCK_CONSUMED");
    this.workOrderId = workOrderId;
    this.stockUnitId = stockUnitId;
    this.batchId = batchId;
    this.consumedWeight = consumedWeight;
    this.unit = unit;
  }
}
