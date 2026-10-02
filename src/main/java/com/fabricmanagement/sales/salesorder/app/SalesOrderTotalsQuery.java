package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotals;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives sales-order totals per agreed currency from the lines. Totals are not stored, so every
 * path that changes a line (edit, customer request, accepted quantity, cancellation) is reflected
 * without a recalculation step. A list loads the lines of all its orders in one query.
 */
@Component
@RequiredArgsConstructor
public class SalesOrderTotalsQuery {

  private final SalesOrderLineRepository lines;

  @Transactional(readOnly = true)
  public Map<UUID, OrderCurrencyTotals> forOrders(UUID tenantId, Collection<UUID> orderIds) {
    if (orderIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<SalesOrderLine>> byOrder =
        lines.findByTenantIdAndSalesOrderIdInAndIsActiveTrue(tenantId, orderIds).stream()
            .collect(Collectors.groupingBy(SalesOrderLine::getSalesOrderId));
    return orderIds.stream()
        .distinct()
        .collect(
            Collectors.toMap(
                Function.identity(),
                id -> OrderCurrencyTotals.of(byOrder.getOrDefault(id, List.of()))));
  }
}
