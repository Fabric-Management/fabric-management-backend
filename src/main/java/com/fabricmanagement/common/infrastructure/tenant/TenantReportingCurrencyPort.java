package com.fabricmanagement.common.infrastructure.tenant;

import java.util.UUID;

/** Port to retrieve the reporting currency of a tenant. */
public interface TenantReportingCurrencyPort {
  String getReportingCurrency(UUID tenantId);
}
