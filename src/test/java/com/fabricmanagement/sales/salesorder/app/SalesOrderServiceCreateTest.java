package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.infrastructure.web.exception.CurrencyMismatchException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation")
class SalesOrderServiceCreateTest {

  @Mock private SalesOrderRepository orderRepository;
  @Mock private TradingPartnerResolver partnerResolver;
  @Mock private TradingPartnerService partnerService;
  @Mock private SalesOrderLineRepository lineRepository;
  @Mock private SalesOrderRuleEngine ruleEngine;
  @Mock private ModuleSpecsValidator moduleSpecsValidator;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private DocumentNumberGenerator documentNumberGenerator;
  @Mock private ApprovalPort approvalPort;
  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;

  @InjectMocks private SalesOrderService salesOrderService;

  @Captor private ArgumentCaptor<SalesOrder> orderCaptor;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID requestPartnerId = UUID.randomUUID();
  private final UUID tradingPartnerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createOrder_calculatesTotalFromLinesAndIgnoresRequestTotalAmount() {
    CreateSalesOrderRequest request = baseRequest();
    request.setTotalAmount(new BigDecimal("9999"));
    request.setTaxAmount(new BigDecimal("5"));
    request.setDiscountAmount(new BigDecimal("1"));
    request.setLines(
        List.of(
            lineRequest(new BigDecimal("3"), new BigDecimal("10.00"), "TRY"),
            lineRequest(new BigDecimal("4"), new BigDecimal("2.50"), "TRY")));
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    verify(orderRepository).save(orderCaptor.capture());
    SalesOrder savedOrder = orderCaptor.getValue();
    assertThat(savedOrder.getTotals().getTotalAmount().getAmount()).isEqualByComparingTo("40.00");
    assertThat(savedOrder.getTotals().getTaxAmount().getAmount()).isEqualByComparingTo("5.00");
    assertThat(savedOrder.getTotals().getDiscountAmount().getAmount()).isEqualByComparingTo("1.00");
  }

  @Test
  void createOrder_lineWithNullUnitPriceContributesZeroToTotal() {
    CreateSalesOrderRequest request = baseRequest();
    request.setTotalAmount(new BigDecimal("9999"));
    request.setLines(
        List.of(
            lineRequest(new BigDecimal("10"), null, null),
            lineRequest(new BigDecimal("2"), new BigDecimal("15"), "TRY")));
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getTotals().getTotalAmount().getAmount())
        .isEqualByComparingTo("30.00");
  }

  @Test
  void createOrder_withoutLinesCalculatesZeroTotal() {
    CreateSalesOrderRequest request = baseRequest();
    request.setTotalAmount(new BigDecimal("9999"));
    request.setLines(List.of());
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getTotals().getTotalAmount().getAmount())
        .isEqualByComparingTo("0.00");
    assertThat(orderCaptor.getValue().getModuleType()).isNull();
  }

  @Test
  void createOrder_homogeneousLineModuleTypes_derivesHeaderModuleType() {
    CreateSalesOrderRequest request = baseRequest();
    request.setModuleType(ModuleType.YARN);
    request.setLines(
        List.of(
            lineRequest(new BigDecimal("3"), new BigDecimal("10.00"), "TRY", ModuleType.FABRIC),
            lineRequest(new BigDecimal("4"), new BigDecimal("2.50"), "TRY", ModuleType.FABRIC)));
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    verify(orderRepository, times(1)).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getModuleType()).isEqualTo(ModuleType.FABRIC);
  }

  @Test
  void createOrder_mixedLineModuleTypes_derivesNullHeaderModuleType() {
    CreateSalesOrderRequest request = baseRequest();
    request.setModuleType(ModuleType.FIBER);
    request.setLines(
        List.of(
            lineRequest(new BigDecimal("3"), new BigDecimal("10.00"), "TRY", ModuleType.FABRIC),
            lineRequest(new BigDecimal("4"), new BigDecimal("2.50"), "TRY", ModuleType.YARN)));
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getModuleType()).isNull();
  }

  @Test
  void createOrder_nullLineModuleTypeIsIgnoredWhenDerivingHeaderModuleType() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(
        List.of(
            lineRequest(new BigDecimal("3"), new BigDecimal("10.00"), "TRY", ModuleType.FABRIC),
            lineRequest(new BigDecimal("4"), new BigDecimal("2.50"), "TRY", null)));
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getModuleType()).isEqualTo(ModuleType.FABRIC);
  }

  @Test
  void createOrder_lineCurrencyMismatchThrowsBeforeDiscountGuard() {
    CreateSalesOrderRequest request = baseRequest();
    request.setDiscountAmount(new BigDecimal("9999"));
    request.setLines(List.of(lineRequest(BigDecimal.ONE, BigDecimal.TEN, "USD")));
    stubCreateUntilTotalCalculation();

    assertThatThrownBy(() -> salesOrderService.createOrder(request))
        .isInstanceOf(CurrencyMismatchException.class);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createOrder_discountGreaterThanCalculatedTotalThrows() {
    CreateSalesOrderRequest request = baseRequest();
    request.setDiscountAmount(new BigDecimal("11.00"));
    request.setLines(List.of(lineRequest(BigDecimal.ONE, BigDecimal.TEN, "TRY")));
    stubCreateUntilTotalCalculation();

    assertThatThrownBy(() -> salesOrderService.createOrder(request))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Discount amount cannot exceed calculated order total");
    verify(orderRepository, never()).save(any());
  }

  private CreateSalesOrderRequest baseRequest() {
    CreateSalesOrderRequest request = new CreateSalesOrderRequest();
    request.setPartnerId(requestPartnerId);
    request.setOrderType(OrderType.SALES);
    request.setOrderDate(LocalDate.of(2026, 6, 1));
    request.setCurrency("TRY");
    return request;
  }

  private SalesOrderLineRequest lineRequest(
      BigDecimal requestedQty, BigDecimal unitPrice, String currency) {
    return lineRequest(requestedQty, unitPrice, currency, null);
  }

  private SalesOrderLineRequest lineRequest(
      BigDecimal requestedQty, BigDecimal unitPrice, String currency, ModuleType moduleType) {
    return SalesOrderLineRequest.builder()
        .productDesc("Cotton fabric")
        .requestedQty(requestedQty)
        .unit("KG")
        .unitPrice(unitPrice)
        .currency(currency)
        .moduleType(moduleType)
        .build();
  }

  private void stubSuccessfulCreate() {
    stubCreateUntilTotalCalculation();
    when(orderRepository.save(any(SalesOrder.class)))
        .thenAnswer(
            invocation -> {
              SalesOrder order = invocation.getArgument(0);
              ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
              return order;
            });
    when(partnerService.findById(tenantId, tradingPartnerId)).thenReturn(Optional.empty());
  }

  private void stubCreateUntilTotalCalculation() {
    when(partnerResolver.resolvePartnerId(tenantId, requestPartnerId)).thenReturn(tradingPartnerId);
    when(documentNumberGenerator.generate(
            tenantId, "SALES_ORDER", "SO", LocalDate.of(2026, 6, 1), 5))
        .thenReturn("SO-20260601-00001");
  }
}
