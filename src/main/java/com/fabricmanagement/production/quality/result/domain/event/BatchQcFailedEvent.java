package com.fabricmanagement.production.quality.result.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Batch kalite kontrolünü geçemedi — CRITICAL. Anında tüm kanallardan, alıcı tercihi değil. QC
 * sorumlusu ve departman yöneticisi hedeflenir.
 */
@Getter
public class BatchQcFailedEvent extends DomainEvent {

  private final UUID batchId;
  private final String batchCode;
  private final String failureReason;
  private final UUID qualityResponsibleUserId;

  public BatchQcFailedEvent(
      UUID tenantId,
      UUID batchId,
      String batchCode,
      String failureReason,
      UUID qualityResponsibleUserId) {
    super(tenantId, "BATCH_QC_FAILED");
    this.batchId = batchId;
    this.batchCode = batchCode;
    this.failureReason = failureReason;
    this.qualityResponsibleUserId = qualityResponsibleUserId;
  }
}
