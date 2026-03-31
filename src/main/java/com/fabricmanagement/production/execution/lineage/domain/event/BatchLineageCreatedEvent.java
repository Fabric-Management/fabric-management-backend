package com.fabricmanagement.production.execution.lineage.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
public class BatchLineageCreatedEvent extends DomainEvent {

  private final UUID lineageId;
  private final UUID parentBatchId;
  private final UUID childBatchId;
  private final BigDecimal consumedQuantity;
  private final String unit;
  private final BigDecimal consumptionPercentage;
  private final Instant consumedAt;

  @Builder
  public BatchLineageCreatedEvent(
      UUID tenantId,
      UUID lineageId,
      UUID parentBatchId,
      UUID childBatchId,
      BigDecimal consumedQuantity,
      String unit,
      BigDecimal consumptionPercentage,
      Instant consumedAt) {
    super(tenantId, "BATCH_LINEAGE_CREATED");
    this.lineageId = lineageId;
    this.parentBatchId = parentBatchId;
    this.childBatchId = childBatchId;
    this.consumedQuantity = consumedQuantity;
    this.unit = unit;
    this.consumptionPercentage = consumptionPercentage;
    this.consumedAt = consumedAt;
  }
}
