package com.fabricmanagement.production.core.stockunit.api;

import java.util.UUID;

/** A permanent owner ending also ends that saved target's provisional business associations. */
public interface StockUnitProvisionalReleasePort {
  void releaseOwnerLine(UUID ownerLineId);
}
