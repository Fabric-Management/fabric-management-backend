package com.fabricmanagement.production.execution.lineage.domain.event;

import java.util.UUID;
import lombok.Value;

@Value
public class BatchLineageDeletedEvent {
  UUID tenantId;
  UUID lineageId;
  UUID parentBatchId;
  UUID childBatchId;
}
