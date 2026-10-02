package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Loads orders and lines for order-intake commands with the sales-order object scope. An order the
 * actor may not read is reported exactly like a missing one.
 */
@Component
@RequiredArgsConstructor
public class OrderIntakeAccess {

  private final SalesOrderRepository orders;
  private final SalesOrderLineRepository lines;
  private final SalesOrderAccessPolicy accessPolicy;

  public SalesOrder readableOrder(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order =
        orders
            .findByTenantIdAndId(tenantId, orderId)
            .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
            .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
    if (!accessPolicy.canRead(tenantId, actor, order)) {
      throw new NotFoundException("Sales order not found: " + orderId);
    }
    return order;
  }

  public SalesOrder writableOrder(UUID orderId, UUID actor) {
    SalesOrder order = readableOrder(orderId, actor);
    if (!accessPolicy.canWrite(TenantContext.requireTenantId(), actor, order)) {
      throw new AccessDeniedException("You do not have access to update this sales order.");
    }
    return order;
  }

  public boolean canWrite(SalesOrder order, UUID actor) {
    return accessPolicy.canWrite(TenantContext.requireTenantId(), actor, order);
  }

  public SalesOrderLine line(SalesOrder order, UUID lineId) {
    return lines
        .findByTenantIdAndId(TenantContext.requireTenantId(), lineId)
        .filter(line -> order.getId().equals(line.getSalesOrderId()))
        .filter(line -> Boolean.TRUE.equals(line.getIsActive()))
        .orElseThrow(() -> new NotFoundException("Sales-order line not found: " + lineId));
  }
}
