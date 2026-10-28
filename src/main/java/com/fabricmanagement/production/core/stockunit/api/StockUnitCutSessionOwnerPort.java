package com.fabricmanagement.production.core.stockunit.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** A read-only association; the common stock reader still validates ownership itself. */
public interface StockUnitCutSessionOwnerPort {
  Map<UUID, UUID> lineOwners(UUID productId, Collection<UUID> sessions);
}
