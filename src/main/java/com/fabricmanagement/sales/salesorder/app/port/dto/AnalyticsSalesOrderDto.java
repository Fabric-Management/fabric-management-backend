package com.fabricmanagement.sales.salesorder.app.port.dto;

import com.fabricmanagement.common.util.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record AnalyticsSalesOrderDto(
    UUID orderId,
    String orderNumber,
    UUID tradingPartnerId,
    UUID quoteId,
    LocalDate orderDate,
    /**
     * Net revenue per agreed currency (subtotal less discount), never pre-converted: the analytics
     * layer converts each to the reporting currency.
     */
    List<Money> netRevenues,
    String status) {}
