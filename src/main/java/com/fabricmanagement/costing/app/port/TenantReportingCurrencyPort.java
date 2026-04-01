package com.fabricmanagement.costing.app.port;

import java.util.UUID;

/** Port to retrieve the reporting currency of a tenant. */
public interface TenantReportingCurrencyPort {
  String getReportingCurrency(UUID tenantId);
}
