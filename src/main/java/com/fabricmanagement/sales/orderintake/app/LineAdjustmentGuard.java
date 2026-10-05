package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.salesorder.app.LineAllocationPolicy;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import org.springframework.stereotype.Component;

/**
 * Checks a line after an accepted quantity changed it: the recorded discount against its quantity
 * (SOI R08: quantity and amount move together) and its delivery allocations (ADR-0014 D8). Order
 * totals need no update: they are derived per currency from the lines on every read.
 */
@Component
class LineAdjustmentGuard {

  private final LineAllocationPolicy allocations;

  LineAdjustmentGuard(LineAllocationPolicy allocations) {
    this.allocations = allocations;
  }

  void assertFits(SalesOrderLine line) {
    allocations.assertChange(line, line.getRequestedQty(), line.getUnit());
    try {
      line.assertAdjustmentsFitQuantity();
    } catch (OrderDomainException ex) {
      throw OrderIntakeException.rule(
          "DISCOUNT_EXCEEDS_TOTAL",
          "The recorded discount would exceed the line amount after this change");
    }
  }
}
