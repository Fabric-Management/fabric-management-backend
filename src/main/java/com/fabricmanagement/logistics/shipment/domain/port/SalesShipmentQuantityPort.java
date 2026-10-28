package com.fabricmanagement.logistics.shipment.domain.port;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Sales owns the commercial decision; logistics owns the sum of actual dispatched quantities. */
public interface SalesShipmentQuantityPort {
  Map<UUID, Limit> lockExactLimits(Collection<UUID> lineIds);

  record Limit(BigDecimal canonicalQuantity, String canonicalUnit, boolean physicalWorkPending) {
    public Limit(BigDecimal quantity, String unit) {
      this(quantity, unit, false);
    }
  }
}
