package com.fabricmanagement.production.execution.lineage.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Value;

@Value
public class BatchLineageCreatedEvent {
  UUID tenantId;
  UUID lineageId;
  UUID parentBatchId;
  UUID childBatchId;
  BigDecimal consumedQuantity;
  String unit;
  BigDecimal consumptionPercentage;
  Instant consumedAt;
}
