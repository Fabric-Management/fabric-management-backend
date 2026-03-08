package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchAdjustedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal delta;
  private final String unit;
  private final UUID locationId;
  private final String reason;
  private final String remarks;

  public BatchAdjustedEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal delta,
      String unit,
      UUID locationId,
      String reason,
      String remarks) {
    super(tenantId, "BATCH_ADJUSTED");
    this.batchId = batchId;
    this.delta = delta;
    this.unit = unit;
    this.locationId = locationId;
    this.reason = reason;
    this.remarks = remarks;
  }
}
