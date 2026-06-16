package com.fabricmanagement.sales.salesorder.app.port;

import com.fabricmanagement.sales.salesorder.app.port.dto.AnalyticsSalesOrderDto;
import java.util.List;
import java.util.UUID;

public interface AnalyticsSalesOrderPort {
  /**
   * Retrieves all open/won orders for a tenant, mapped to a lightweight analytics DTO. DRAFT and
   * CANCELLED orders are excluded.
   */
  List<AnalyticsSalesOrderDto> getOrdersForAnalytics(UUID tenantId);
}
