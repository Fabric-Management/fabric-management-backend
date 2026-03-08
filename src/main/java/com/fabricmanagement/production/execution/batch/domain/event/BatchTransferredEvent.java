package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchTransferredEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID oldLocationId;
  private final UUID newLocationId;
  private final String remarks;

  public BatchTransferredEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal quantity,
      String unit,
      UUID oldLocationId,
      UUID newLocationId,
      String remarks) {
    super(tenantId, "BATCH_TRANSFERRED");
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.oldLocationId = oldLocationId;
    this.newLocationId = newLocationId;
    this.remarks = remarks;
  }
}
