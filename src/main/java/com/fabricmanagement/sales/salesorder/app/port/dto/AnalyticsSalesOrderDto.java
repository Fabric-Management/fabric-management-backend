package com.fabricmanagement.sales.salesorder.app.port.dto;

import com.fabricmanagement.common.util.Money;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record AnalyticsSalesOrderDto(
    UUID orderId,
    String orderNumber,
    UUID tradingPartnerId,
    UUID quoteId,
    LocalDate orderDate,
    Money netRevenue,
    String status) {}
