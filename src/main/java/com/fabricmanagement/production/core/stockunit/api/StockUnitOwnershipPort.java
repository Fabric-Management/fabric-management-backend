package com.fabricmanagement.production.core.stockunit.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The common stock-writer gate; provisional leases never bypass a permanent commitment. */
public interface StockUnitOwnershipPort {
  void lockProduct(UUID productId);

  String availabilityRevision(UUID productId);

  void lockLots(Collection<UUID> batchIds);

  void assertAnonymousLotsFree(Collection<UUID> batchIds);

  void assertAvailable(Collection<UUID> pieceIds, UUID ownLineId, UUID ownSessionId);

  /** Checks all stock owners before permitting consumption against a work-order reservation. */
  void assertReservedConsumption(UUID pieceId, UUID workOrderId);

  Set<UUID> provisionallyHeld(UUID productId, UUID ownSessionId);

  List<UUID> productIdsForLots(Collection<UUID> batchIds);
}
