package com.fabricmanagement.flowboard.generator.domain.port.out;

import java.util.List;
import java.util.UUID;

/** Flowboard owns its read contract; sales resolves quote conversion and current requirements. */
public interface SalesStockPlanningQueryPort {
  UUID logicalId(UUID documentId, boolean quote);

  Work current(UUID documentId, boolean quote);

  record Work(
      UUID logicalId,
      String entityType,
      UUID entityId,
      String reference,
      boolean active,
      List<String> requirements) {
    public Work {
      requirements = List.copyOf(requirements);
    }
  }
}
