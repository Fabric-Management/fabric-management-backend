package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchConsumedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID locationId;
  private final UUID referenceId;
  private final String referenceType;

  public BatchConsumedEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal quantity,
      String unit,
      UUID locationId,
      UUID referenceId,
      String referenceType) {
    super(tenantId, "BATCH_CONSUMED");
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
    this.referenceId = referenceId;
    this.referenceType = referenceType;
  }
}
