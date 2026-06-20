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
    /**
     * Returns all non-DRAFT, non-CANCELLED orders for analytics. DELIVERED orders are intentionally
     * included — the analytics layer applies its own backlog filter (EXCLUDED_BACKLOG_STATUSES) to
     * separate backlog from fulfilled orders.
     */
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
                    .status(o.getStatus() != null ? o.getStatus().name() : null)
                    .build())
        .toList();
  }
}
