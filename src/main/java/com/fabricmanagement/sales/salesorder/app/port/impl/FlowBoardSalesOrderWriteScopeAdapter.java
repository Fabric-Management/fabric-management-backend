package com.fabricmanagement.sales.salesorder.app.port.impl;

import com.fabricmanagement.flowboard.routing.domain.port.out.SalesOrderWriteScopePort;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy.PermissionFreshness;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** Sales-owned implementation of FlowBoard's order write-scope boundary. */
@Component
@RequiredArgsConstructor
public class FlowBoardSalesOrderWriteScopeAdapter implements SalesOrderWriteScopePort {

  private final SalesOrderRepository orderRepository;
  private final SalesOrderAccessPolicy accessPolicy;

  @Override
  public Set<UUID> allowedOrderIds(UUID tenantId, UUID userId, Collection<UUID> orderIds) {
    if (tenantId == null || userId == null || orderIds == null || orderIds.isEmpty())
      return Set.of();
    if (orderIds.size() > 100)
      throw new IllegalArgumentException("At most 100 order ids are allowed");
    Specification<com.fabricmanagement.sales.salesorder.domain.SalesOrder> selected =
        (root, query, builder) -> root.get("id").in(orderIds);
    return orderRepository
        .findAll(
            accessPolicy
                .writeRestriction(tenantId, userId, PermissionFreshness.FRESH)
                .and(selected))
        .stream()
        .map(com.fabricmanagement.sales.salesorder.domain.SalesOrder::getId)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
