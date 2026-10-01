package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** The order's amounts in one agreed currency, derived from its lines; never converted. */
@Schema(description = "Order totals in one agreed currency, derived from the lines")
public record SalesOrderCurrencyTotalDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "USD") String currency,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Σ quantity × unit price")
        BigDecimal subtotal,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal discountAmount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal taxAmount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Subtotal less discount")
        BigDecimal netAmount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Net plus tax")
        BigDecimal grandTotal) {

  public static SalesOrderCurrencyTotalDto from(OrderCurrencyTotal total) {
    return new SalesOrderCurrencyTotalDto(
        total.currency(),
        total.subtotal(),
        total.discount(),
        total.tax(),
        total.net(),
        total.grandTotal());
  }
}
