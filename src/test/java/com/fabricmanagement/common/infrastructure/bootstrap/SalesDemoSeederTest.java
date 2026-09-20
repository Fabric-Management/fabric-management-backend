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

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.product.core.api.facade.ProductFacade;
import com.fabricmanagement.product.core.domain.ProductType;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void seederPreservesRealConfirmationFlowWithAndWithoutApproval(boolean approvalRequired) {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(SystemUser.ID);
    SalesOrderRepository orders = mock(SalesOrderRepository.class);
    SalesOrderLineRepository lines = mock(SalesOrderLineRepository.class);
    ApprovalPort approval = mock(ApprovalPort.class);
    Map<UUID, SalesOrder> stored = new LinkedHashMap<>();
    when(orders.findByTenantIdAndId(eq(TENANT_ID), any()))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(1))));
    when(orders.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(approval.requiresApproval(any(), any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat((UUID) invocation.getArgument(1)).isEqualTo(SystemUser.ID);
              return approvalRequired;
            });
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
                approval,
                null,
                mock(SalesOrderAccessPolicy.class),
                mock(
                    com.fabricmanagement.sales.salesorder.infra.repository
                        .OrderCoverActivationRepository.class),
                enrolment));
    // Isolate creation only; seedFor invokes the real demo entry and shared approval flow.
    doAnswer(
            invocation -> {
              CreateSalesOrderRequest request = invocation.getArgument(0);
              SalesOrder order =
                  SalesOrder.builder()
                      .tradingPartnerId(request.getPartnerId())
                      .orderNumber("SO-SEED-" + stored.size())
                      .totals(OrderTotals.zero(request.getCurrency()))
                      .build();
              order.setId(UUID.randomUUID());
              order.setTenantId(TENANT_ID);
              stored.put(order.getId(), order);
              return SalesOrderDto.from(order);
            })
        .when(realConfirmation)
        .createOrder(any());
    ProductDto templateFiber = fiber();
    when(productFacade.findByType(any(), eq(ProductType.FIBER))).thenReturn(List.of(templateFiber));
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
        .allSatisfy(
            order ->
                assertThat(order.getStatus())
                    .isEqualTo(
                        approvalRequired ? OrderStatus.PENDING_APPROVAL : OrderStatus.CONFIRMED));
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
    verify(salesOrderService, times(3)).confirmDemoSeedOrder(any());
  }

  @Test
  void skipsWhenNoTemplateFibers() {
    when(productFacade.findByType(any(), eq(ProductType.FIBER))).thenReturn(List.of());

    seeder.seedFor(TENANT_ID);

    verify(salesOrderService, never()).createOrder(any());
    verify(salesOrderService, never()).confirmDemoSeedOrder(any());
  }
}
