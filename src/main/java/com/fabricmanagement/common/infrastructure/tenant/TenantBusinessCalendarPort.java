package com.fabricmanagement.common.infrastructure.tenant;

import java.time.ZoneId;

/**
 * The bound tenant's business calendar, shared by finance, sales and work queues. Business dates
 * use the tenant's zone, never the viewer's display zone nor the server's.
 */
public interface TenantBusinessCalendarPort {
  ZoneId zone();
}
