package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/** Published when a StockUnit is recorded as a production record for a WorkOrder. */
@Getter
public class ProductionRecordedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final UUID stockUnitId;
  private final UUID batchId;
  private final BigDecimal outputWeight;
  private final String unit;

  public ProductionRecordedEvent(
      UUID tenantId,
      UUID workOrderId,
      UUID stockUnitId,
      UUID batchId,
      BigDecimal outputWeight,
      String unit) {
    super(tenantId, "PRODUCTION_RECORDED");
    this.workOrderId = workOrderId;
    this.stockUnitId = stockUnitId;
    this.batchId = batchId;
    this.outputWeight = outputWeight;
    this.unit = unit;
  }
}
