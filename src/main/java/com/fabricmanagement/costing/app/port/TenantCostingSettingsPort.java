package com.fabricmanagement.costing.app.port;

import java.math.BigDecimal;
import java.util.UUID;

/** Port to retrieve tenant-specific costing configuration. */
public interface TenantCostingSettingsPort {
  /**
   * @return variance threshold (fractional, e.g. 0.10 = 10%). Never null — adapter resolves null →
   *     platform default.
   */
  BigDecimal getVarianceThreshold(UUID tenantId);
}
