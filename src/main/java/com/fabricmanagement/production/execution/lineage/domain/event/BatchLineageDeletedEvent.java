package com.fabricmanagement.production.execution.lineage.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
public class BatchLineageDeletedEvent extends DomainEvent {

  private final UUID lineageId;
  private final UUID parentBatchId;
  private final UUID childBatchId;

  @Builder
  public BatchLineageDeletedEvent(
      UUID tenantId, UUID lineageId, UUID parentBatchId, UUID childBatchId) {
    super(tenantId, "BATCH_LINEAGE_DELETED");
    this.lineageId = lineageId;
    this.parentBatchId = parentBatchId;
    this.childBatchId = childBatchId;
  }
}
