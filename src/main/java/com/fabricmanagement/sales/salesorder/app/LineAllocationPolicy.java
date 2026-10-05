package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.OrderLineAllocation;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderLineAllocationRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps a line and its delivery allocations consistent (ADR-0014 D8). Every path that changes a
 * line's quantity or unit asks here first; a change the deliveries no longer fit is rejected, and
 * the allocations are corrected explicitly before it.
 */
@Component
@RequiredArgsConstructor
public class LineAllocationPolicy {

  private final OrderLineAllocationRepository allocations;

  /**
   * Rejects changing {@code line} to {@code quantity} in {@code unit} when its deliveries carry
   * more than that, or carry it in another unit. Call it before the line is changed.
   */
  public void assertChange(SalesOrderLine line, BigDecimal quantity, String unit) {
    if (line.getId() == null) {
      return;
    }
    BigDecimal allocated = allocations.sumByLine(TenantContext.requireTenantId(), line.getId());
    OrderLineAllocation.assertLineChangeFits(allocated, line.getUnit(), quantity, unit);
  }

  /** Removes the allocations of lines that left the order. */
  public void linesRemoved(UUID orderId, Collection<UUID> lineIds) {
    if (!lineIds.isEmpty()) {
      allocations.deleteByLines(TenantContext.requireTenantId(), orderId, lineIds);
    }
  }
}
