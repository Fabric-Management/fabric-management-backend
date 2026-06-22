package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesDemoSeederTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private TradingPartnerService tradingPartnerService;
  @Mock private ProductFacade productFacade;
  @Mock private SalesOrderService salesOrderService;

  private SalesDemoSeeder seeder;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-06-21T10:00:00Z"), ZoneId.of("UTC"));
    seeder = new SalesDemoSeeder(tradingPartnerService, productFacade, salesOrderService, clock);
  }

  private ProductDto fiber() {
    ProductDto p = mock(ProductDto.class);
    when(p.getId()).thenReturn(UUID.randomUUID());
    return p;
  }

  @Test
  void createsAndConfirmsOneOrderPerDemoCustomer() {
    ProductDto fiber1 = fiber();
    ProductDto fiber2 = fiber();
    when(productFacade.findByType(any(), eq(ProductType.FIBER)))
        .thenReturn(List.of(fiber1, fiber2));
    when(tradingPartnerService.searchByName(any(), any()))
        .thenReturn(List.of(TradingPartnerDto.builder().id(UUID.randomUUID()).build()));
    when(salesOrderService.createOrder(any(CreateSalesOrderRequest.class)))
        .thenAnswer(i -> SalesOrderDto.builder().id(UUID.randomUUID()).build());

    seeder.seedFor(TENANT_ID);

    verify(salesOrderService, times(3)).createOrder(any());
    verify(salesOrderService, times(3)).confirmOrder(any());
  }

  @Test
  void skipsWhenNoTemplateFibers() {
    when(productFacade.findByType(any(), eq(ProductType.FIBER))).thenReturn(List.of());

    seeder.seedFor(TENANT_ID);

    verify(salesOrderService, never()).createOrder(any());
    verify(salesOrderService, never()).confirmOrder(any());
  }
}
