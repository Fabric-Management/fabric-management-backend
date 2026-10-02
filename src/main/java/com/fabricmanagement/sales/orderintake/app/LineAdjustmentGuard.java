package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import org.springframework.stereotype.Component;

/**
 * Checks a line's recorded discount against its quantity after an accepted quantity changed it (SOI
 * R08: quantity and amount move together). Order totals need no update: they are derived per
 * currency from the lines on every read.
 */
@Component
class LineAdjustmentGuard {

  void assertFits(SalesOrderLine line) {
    try {
      line.assertAdjustmentsFitQuantity();
    } catch (OrderDomainException ex) {
      throw OrderIntakeException.rule(
          "DISCOUNT_EXCEEDS_TOTAL",
          "The recorded discount would exceed the line amount after this change");
    }
  }
}
