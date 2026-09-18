package com.fabricmanagement.flowboard.routing.domain.port.out;

import java.util.UUID;

/** Consumer-owned boundary for fresh, explicit-user sales-order write-scope evaluation. */
public interface SalesOrderWriteScopePort {

  boolean isAllowed(UUID tenantId, UUID userId, UUID orderId);
}
