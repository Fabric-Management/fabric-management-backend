package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchCreatedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID locationId;

  public BatchCreatedEvent(
      UUID tenantId, UUID batchId, BigDecimal quantity, String unit, UUID locationId) {
    super(tenantId, "BATCH_CREATED");
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
  }
}
