package com.fabricmanagement.production.core.workorder.api;

import java.util.UUID;

/** Sales owns the current product of a sales line; production cannot silently use an old one. */
public interface WorkOrderSalesProductPort {
  boolean matchesCurrentProduct(UUID tenantId, UUID lineId, UUID outputProductId);
}
