package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchSplitEvent extends DomainEvent {
  private final UUID parentBatchId;
  private final UUID childBatchId;
  private final BigDecimal splitQuantity;
  private final String unit;
  private final UUID parentLocationId;
  private final UUID childLocationId;
  private final String parentBatchCode;
  private final String childBatchCode;
  private final String remarks;

  public BatchSplitEvent(
      UUID tenantId,
      UUID parentBatchId,
      UUID childBatchId,
      BigDecimal splitQuantity,
      String unit,
      UUID parentLocationId,
      UUID childLocationId,
      String parentBatchCode,
      String childBatchCode,
      String remarks) {
    super(tenantId, "BATCH_SPLIT");
    this.parentBatchId = parentBatchId;
    this.childBatchId = childBatchId;
    this.splitQuantity = splitQuantity;
    this.unit = unit;
    this.parentLocationId = parentLocationId;
    this.childLocationId = childLocationId;
    this.parentBatchCode = parentBatchCode;
    this.childBatchCode = childBatchCode;
    this.remarks = remarks;
  }
}
