package com.fabricmanagement.sales.salesorder.domain.port;

import java.util.UUID;

public interface ProductionOrderPort {
  UUID requestDraftProductionOrder(DraftProductionOrderCommand command);

  default boolean hasActiveProduction(UUID tenantId, UUID salesOrderLineId) {
    return false;
  }
}
