package com.fabricmanagement.sales.salesorder.app.port.impl;

import com.fabricmanagement.sales.salesorder.app.port.AnalyticsSalesOrderPort;
import com.fabricmanagement.sales.salesorder.app.port.dto.AnalyticsSalesOrderDto;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsSalesOrderPortImpl implements AnalyticsSalesOrderPort {

  private final SalesOrderRepository salesOrderRepository;

  @Override
  @Transactional(readOnly = true)
  public List<AnalyticsSalesOrderDto> getOrdersForAnalytics(UUID tenantId) {
    // findOpenOrders excludes DELIVERED, CANCELLED.
    // Wait, the ticket says "open/won orders". DRAFT and CANCELLED should be excluded.
    // DELIVERED should probably be included since they are won orders!
    // So we can't use findOpenOrders if it excludes DELIVERED.
    // We should write a query or use findAll and filter, or findByTenantId.
    // Let's use findByTenantIdAndIsActiveTrue and filter, or a custom repository call if needed.
    // For simplicity here, we'll fetch all active and filter out DRAFT and CANCELLED.
    return salesOrderRepository
        .findByTenantIdAndIsActiveTrue(tenantId, org.springframework.data.domain.Pageable.unpaged())
        .stream()
        .filter(o -> o.getStatus() != OrderStatus.DRAFT && o.getStatus() != OrderStatus.CANCELLED)
        .map(
            o ->
                AnalyticsSalesOrderDto.builder()
                    .orderId(o.getId())
                    .orderNumber(o.getOrderNumber())
                    .tradingPartnerId(o.getTradingPartnerId())
                    .quoteId(o.getQuoteId())
                    .orderDate(o.getOrderDate())
                    .netRevenue(o.getNetTotal())
                    .status(o.getStatus())
                    .build())
        .toList();
  }
}
