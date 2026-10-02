package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.product.core.api.facade.ProductFacade;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.sales.salesorder.app.OrderCoverEnrolmentService;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void seederConfirmsSeededOrdersWithoutTheCustomerApprovalFlow() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(SystemUser.ID);
    SalesOrderRepository orders = mock(SalesOrderRepository.class);
    SalesOrderLineRepository lines = mock(SalesOrderLineRepository.class);
    Map<UUID, SalesOrder> stored = new LinkedHashMap<>();
    when(orders.findByTenantIdAndId(eq(TENANT_ID), any()))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(1))));
    when(orders.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    OrderCoverEnrolmentService enrolment = mock(OrderCoverEnrolmentService.class);
    when(enrolment.decide(any(), any()))
        .thenReturn(com.fabricmanagement.sales.salesorder.domain.OrderCoverRegime.LEGACY);
    SalesOrderService realConfirmation =
        spy(
            new SalesOrderService(
                orders,
                null,
                tradingPartnerService,
                lines,
                mock(SalesOrderRuleEngine.class),
                null,
                null,
                mock(DomainEventPublisher.class),
                null,
                null,
                mock(com.fabricmanagement.sales.salesorder.app.SalesOrderRevision.class),
                mock(SalesOrderAccessPolicy.class),
                mock(com.fabricmanagement.sales.salesorder.app.DeliveryCommitmentService.class),
                mock(
                    com.fabricmanagement.sales.salesorder.infra.repository
                        .OrderCoverActivationRepository.class),
                enrolment,
                mock(com.fabricmanagement.sales.salesorder.app.OrderIntakeHooks.class),
                mock(com.fabricmanagement.sales.orderintake.app.CustomerRequestService.class),
                mock(com.fabricmanagement.sales.salesorder.app.OrderApprovalInvalidator.class)));
    // Isolate creation only; seedFor invokes the real demo confirmation.
    doAnswer(
            invocation -> {
              CreateSalesOrderRequest request = invocation.getArgument(0);
              SalesOrder order =
                  SalesOrder.builder()
                      .tradingPartnerId(request.getPartnerId())
                      .orderNumber("SO-SEED-" + stored.size())
                      .build();
              order.setId(UUID.randomUUID());
              order.setTenantId(TENANT_ID);
              stored.put(order.getId(), order);
              return SalesOrderDto.from(
                  order, com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotals.EMPTY);
            })
        .when(realConfirmation)
        .createOrder(any());
    ProductDto sharedFiber = fiber();
    when(productFacade.findCanonicalFiberProduct(any())).thenReturn(Optional.of(sharedFiber));
    when(tradingPartnerService.searchByName(any(), any()))
        .thenReturn(List.of(TradingPartnerDto.builder().id(UUID.randomUUID()).build()));
    SalesDemoSeeder realFlowSeeder =
        new SalesDemoSeeder(
            tradingPartnerService,
            productFacade,
            realConfirmation,
            Clock.fixed(Instant.parse("2026-06-21T10:00:00Z"), ZoneId.of("UTC")));

    realFlowSeeder.seedFor(TENANT_ID);

    assertThat(stored.values())
        .hasSize(3)
        .allSatisfy(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED));
  }

  @Test
  void createsAndConfirmsOneOrderPerDemoCustomer() {
    // FIBER-CATALOG-1: exact shared cotton and polyester by ISO code, not list positions.
    ProductDto cotton = fiber();
    ProductDto polyester = fiber();
    when(productFacade.findCanonicalFiberProduct("CO")).thenReturn(Optional.of(cotton));
    when(productFacade.findCanonicalFiberProduct("PES")).thenReturn(Optional.of(polyester));
    when(tradingPartnerService.searchByName(any(), any()))
        .thenReturn(List.of(TradingPartnerDto.builder().id(UUID.randomUUID()).build()));
    when(salesOrderService.createOrder(any(CreateSalesOrderRequest.class)))
        .thenAnswer(i -> SalesOrderDto.builder().id(UUID.randomUUID()).build());

    seeder.seedFor(TENANT_ID);

    verify(salesOrderService, times(3)).createOrder(any());
    verify(salesOrderService, times(3)).confirmDemoSeedOrder(any());
  }

  @Test
  void skipsWhenTheSharedCottonOrPolyesterIsNotPublished() {
    when(productFacade.findCanonicalFiberProduct(any())).thenReturn(Optional.empty());

    seeder.seedFor(TENANT_ID);

    verify(salesOrderService, never()).createOrder(any());
    verify(salesOrderService, never()).confirmDemoSeedOrder(any());
  }
}
