package com.fabricmanagement.sales.salesorder.app.port.impl;

import com.fabricmanagement.flowboard.routing.domain.port.out.SalesOrderWriteScopePort;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy.PermissionFreshness;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Sales-owned implementation of FlowBoard's order write-scope boundary. */
@Component
@RequiredArgsConstructor
public class FlowBoardSalesOrderWriteScopeAdapter implements SalesOrderWriteScopePort {

  private final SalesOrderRepository orderRepository;
  private final SalesOrderAccessPolicy accessPolicy;

  @Override
  public boolean isAllowed(UUID tenantId, UUID userId, UUID orderId) {
    if (tenantId == null || userId == null || orderId == null) {
      return false;
    }
    return orderRepository
        .findByTenantIdAndId(tenantId, orderId)
        .map(order -> accessPolicy.canWrite(tenantId, userId, order, PermissionFreshness.FRESH))
        .orElse(false);
  }
}
