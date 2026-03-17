package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.execution.batch.domain.WasteCategory;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchWasteRecordedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID locationId;
  private final WasteCategory wasteCategory;
  private final String reason;

  public BatchWasteRecordedEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal quantity,
      String unit,
      UUID locationId,
      WasteCategory wasteCategory,
      String reason) {
    super(tenantId, "BATCH_WASTE_RECORDED");
    this.batchId = batchId;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
    this.wasteCategory = wasteCategory;
    this.reason = reason;
  }
}
