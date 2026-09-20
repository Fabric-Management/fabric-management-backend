package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCoverObjectAccess {
  private final SalesOrderRepository orders;
  private final SalesOrderAccessPolicy policy;

  public SalesOrder readable(UUID orderId, UUID actorId) {
    UUID tenant = TenantContext.requireTenantId();
    SalesOrder order =
        orders
            .findByTenantIdAndId(tenant, orderId)
            .orElseThrow(() -> new NotFoundException("Sales order not found"));
    if (!policy.canRead(tenant, actorId, order))
      throw new NotFoundException("Sales order not found");
    return order;
  }

  public void assertWritable(UUID orderId, UUID actorId) {
    SalesOrder order = readable(orderId, actorId);
    UUID tenant = TenantContext.requireTenantId();
    if (!policy.canWrite(tenant, actorId, order, SalesOrderAccessPolicy.PermissionFreshness.FRESH))
      throw new AccessDeniedException("Sales-order write scope does not admit this order");
  }
}
