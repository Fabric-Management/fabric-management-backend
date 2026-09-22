package com.fabricmanagement.flowboard.routing.domain.port.out;

import java.util.*;

/** Consumer-owned boundary for fresh, explicit-user sales-order write-scope evaluation. */
public interface SalesOrderWriteScopePort {

  Set<UUID> allowedOrderIds(UUID tenantId, UUID userId, Collection<UUID> orderIds);

  default boolean isAllowed(UUID tenantId, UUID userId, UUID orderId) {
    return orderId != null && allowedOrderIds(tenantId, userId, Set.of(orderId)).contains(orderId);
  }
}
