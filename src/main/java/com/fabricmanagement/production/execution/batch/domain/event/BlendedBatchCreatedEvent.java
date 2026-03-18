package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** Published after a blended batch is created (multiple parents consumed → one child batch). */
@Getter
public class BlendedBatchCreatedEvent extends DomainEvent {
  private final UUID childBatchId;
  private final List<UUID> parentIds;
  private final BigDecimal totalQuantity;
  private final String unit;

  public BlendedBatchCreatedEvent(
      UUID tenantId,
      UUID childBatchId,
      List<UUID> parentIds,
      BigDecimal totalQuantity,
      String unit) {
    super(tenantId, "BLENDED_BATCH_CREATED");
    this.childBatchId = childBatchId;
    this.parentIds = parentIds != null ? List.copyOf(parentIds) : List.of();
    this.totalQuantity = totalQuantity;
    this.unit = unit;
  }
}
