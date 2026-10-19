package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.product.color.api.query.ColorQueryService;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrderVersionStockQuantityTest {
  @Test
  void snapshotKeepsCustomerRequestAndUsesAppliedQuantityForTheBritishPoundTotal() {
    UUID tenant = UUID.randomUUID(), customer = UUID.randomUUID();
    var order = SalesOrder.builder().tradingPartnerId(customer).orderNumber("SO-2026-001").build();
    order.setId(UUID.randomUUID());
    var line =
        SalesOrderLine.builder()
            .productId(UUID.randomUUID())
            .productDesc("Cotton poplin")
            .requestedQty(new BigDecimal("665"))
            .customerRequestQty(new BigDecimal("700"))
            .unit("M")
            .currency("GBP")
            .unitPriceAmount(new BigDecimal("8.60"))
            .toleranceUpPct(new BigDecimal("5"))
            .toleranceDownPct(new BigDecimal("5"))
            .build();
    line.setId(UUID.randomUUID());
    line.captureInitialRequestIfAbsent();
    var lines = mock(SalesOrderLineRepository.class);
    var partners = mock(TradingPartnerService.class);
    var requests = mock(CustomerProductRequestRepository.class);
    var tenants = mock(TenantQueryPort.class);
    when(lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId()))
        .thenReturn(List.of(line));
    when(partners.findById(tenant, customer)).thenReturn(Optional.empty());
    when(tenants.findById(tenant)).thenReturn(Optional.empty());
    when(requests.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByRecordedAtAscIdAsc(
            tenant, order.getId()))
        .thenReturn(List.of());
    var snapshots =
        new OrderVersionSnapshotter(
            lines,
            partners,
            mock(ColorQueryService.class),
            requests,
            tenants,
            new ObjectMapper().findAndRegisterModules());
    var content = snapshots.snapshot(tenant, order, null).content();
    assertThat(line.getInitialRequestedQty()).isEqualByComparingTo("700");
    assertThat(content.lines().getFirst().requestQuantity()).isEqualByComparingTo("700");
    assertThat(content.lines().getFirst().quantity()).isEqualByComparingTo("665");
    assertThat(content.lines().getFirst().toleranceUpPct()).isEqualByComparingTo("5");
    assertThat(content.totals().getFirst().total()).isEqualByComparingTo("5719.00");
  }
}
