package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchProductionStartedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID previousLocationId;
  private final UUID machineLocationId;
  private final String machineCode;

  public BatchProductionStartedEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal quantity,
      String unit,
      UUID previousLocationId,
      UUID machineLocationId,
      String machineCode) {
    super(tenantId, "BATCH_PRODUCTION_STARTED");
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.previousLocationId = previousLocationId;
    this.machineLocationId = machineLocationId;
    this.machineCode = machineCode;
  }
}
