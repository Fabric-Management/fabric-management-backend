package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Recomputes the order total from its lines after an accepted quantity changed a line (SOI R08:
 * quantity and amount move together). Tax and discount stay as recorded.
 */
@Component
@RequiredArgsConstructor
class OrderTotalsRecalculator {

  private final SalesOrderLineRepository lines;

  void recalculate(SalesOrder order) {
    String currency = order.getCurrency();
    BigDecimal total =
        lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId()).stream()
            .filter(line -> line.getUnitPrice() != null && line.getRequestedQty() != null)
            .map(line -> line.getUnitPrice().getAmount().multiply(line.getRequestedQty()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    OrderTotals current = order.getTotals();
    Money tax = current == null ? Money.zero(currency) : current.getTaxAmount();
    Money discount = current == null ? Money.zero(currency) : current.getDiscountAmount();
    if (discount.getAmount().compareTo(total) > 0) {
      throw OrderIntakeException.rule(
          "DISCOUNT_EXCEEDS_TOTAL",
          "The recorded discount would exceed the order total after this change");
    }
    order.updateTotals(OrderTotals.of(Money.of(total, currency), tax, discount));
  }
}
