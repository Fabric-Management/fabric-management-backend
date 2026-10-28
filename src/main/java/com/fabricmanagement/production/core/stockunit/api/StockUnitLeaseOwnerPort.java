package com.fabricmanagement.production.core.stockunit.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Server-owned business associations, never ownership claims supplied by a stock reader. */
public interface StockUnitLeaseOwnerPort {
  Map<UUID, UUID> lineOwners(UUID productId, Collection<UUID> sessionIds);
}
