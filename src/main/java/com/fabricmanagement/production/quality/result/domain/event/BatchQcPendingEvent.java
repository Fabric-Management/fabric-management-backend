package com.fabricmanagement.production.quality.result.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** Batch QC bekliyor — NORMAL. QC sorumlusuna bildirim. */
@Getter
public class BatchQcPendingEvent extends DomainEvent {

  private final UUID batchId;
  private final String batchCode;
  private final UUID qualityResponsibleUserId;

  public BatchQcPendingEvent(
      UUID tenantId, UUID batchId, String batchCode, UUID qualityResponsibleUserId) {
    super(tenantId, "BATCH_QC_PENDING");
    this.batchId = batchId;
    this.batchCode = batchCode;
    this.qualityResponsibleUserId = qualityResponsibleUserId;
  }
}
