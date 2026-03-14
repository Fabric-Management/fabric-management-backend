package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a batch reaches terminal completion (status DEPLETED). Used to snapshot
 * certifications for GOTS TC: freeze cert number and valid-until at completion time.
 */
@Getter
public class BatchCompletedEvent extends DomainEvent {
  private final UUID batchId;

  public BatchCompletedEvent(UUID tenantId, UUID batchId) {
    super(tenantId, "BATCH_COMPLETED");
    this.batchId = batchId;
  }
}
