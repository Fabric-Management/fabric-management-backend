package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchLotQuantityIntentReleasedEvent extends DomainEvent {
  private final UUID quoteId;
  private final UUID quoteLineId;
  private final UUID batchId;
  private final UUID intentId;

  public BatchLotQuantityIntentReleasedEvent(
      UUID tenantId, UUID quoteId, UUID quoteLineId, UUID batchId, UUID intentId) {
    super(tenantId, "BATCH_LOT_QUANTITY_INTENT_RELEASED");
    this.quoteId = quoteId;
    this.quoteLineId = quoteLineId;
    this.batchId = batchId;
    this.intentId = intentId;
  }
}
