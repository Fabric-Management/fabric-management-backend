package com.fabricmanagement.product.fiber.app.port;

import java.util.UUID;

/** Production usage boundary used to protect immutable fiber definitions. */
public interface FiberUsagePort {

  boolean isFiberInActiveProduction(UUID tenantId, UUID productId);
}
