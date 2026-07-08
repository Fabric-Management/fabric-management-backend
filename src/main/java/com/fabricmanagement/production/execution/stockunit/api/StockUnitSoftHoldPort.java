package com.fabricmanagement.production.execution.stockunit.api;

import java.util.Collection;
import java.util.UUID;

public interface StockUnitSoftHoldPort {

  void replaceHolds(UUID quoteLineId, Collection<UUID> stockUnitIds);

  void releaseHolds(UUID quoteLineId);
}
