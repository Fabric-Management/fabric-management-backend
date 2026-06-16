package com.fabricmanagement.analytics.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.analytics.dto.EstimatedMarginResponse;
import com.fabricmanagement.analytics.dto.MarginWarningDto;
import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.app.port.AnalyticsCostingPort;
import com.fabricmanagement.costing.app.port.dto.AnalyticsCostEstimateDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.sales.salesorder.app.port.AnalyticsSalesOrderPort;
import com.fabricmanagement.sales.salesorder.app.port.dto.AnalyticsSalesOrderDto;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EstimatedMarginServiceTest {

  @Mock private AnalyticsSalesOrderPort salesOrderPort;
  @Mock private AnalyticsCostingPort costingPort;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private TradingPartnerResolver partnerResolver;
  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;

  @InjectMocks private EstimatedMarginService estimatedMarginService;

  private final UUID tenantId = UUID.randomUUID();
  private final String tenantCurrency = "USD";

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void getEstimatedMargin_shouldCalculateMarginProperly() {
    // Arrange
    UUID orderId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();

    AnalyticsSalesOrderDto orderDto =
        AnalyticsSalesOrderDto.builder()
            .orderId(orderId)
            .orderNumber("SO-001")
            .tradingPartnerId(partnerId)
            .quoteId(quoteId)
            .orderDate(LocalDate.now())
            .netRevenue(Money.of(new BigDecimal("100.00"), "USD"))
            .status(OrderStatus.CONFIRMED)
            .build();

    when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn(tenantCurrency);
    when(salesOrderPort.getOrdersForAnalytics(tenantId)).thenReturn(List.of(orderDto));

    AnalyticsCostEstimateDto costDto =
        AnalyticsCostEstimateDto.builder()
            .totalCost(Money.of(new BigDecimal("60.00"), "USD"))
            .complete(true)
            .build();

    when(costingPort.getEstimatedCostsByQuoteIds(tenantId, Set.of(quoteId)))
        .thenReturn(Map.of(quoteId, costDto));

    when(partnerResolver.resolveDisplayNames(eq(tenantId), any()))
        .thenReturn(Map.of(partnerId, "Partner A"));

    when(exchangeRateService.convert(
            eq(tenantId),
            eq(orderDto.netRevenue().getAmount()),
            eq(orderDto.netRevenue().getCurrency().getCurrencyCode()),
            eq(tenantCurrency),
            any()))
        .thenReturn(ConvertedMoney.sameUnit(new BigDecimal("100.00"), "USD"));

    when(exchangeRateService.convert(
            eq(tenantId),
            eq(costDto.totalCost().getAmount()),
            eq(costDto.totalCost().getCurrency().getCurrencyCode()),
            eq(tenantCurrency),
            any()))
        .thenReturn(ConvertedMoney.sameUnit(new BigDecimal("60.00"), "USD"));

    // Act
    EstimatedMarginResponse response = estimatedMarginService.getEstimatedMargin();

    // Assert
    assertThat(response.reportingCurrency()).isEqualTo("USD");
    assertThat(response.orders()).hasSize(1);
    assertThat(response.orders().get(0).estimatedMargin().getAmount())
        .isEqualByComparingTo("40.00");
    assertThat(response.orders().get(0).estimatedMarginPercentage()).isEqualByComparingTo("40.00");
    assertThat(response.orders().get(0).costIncomplete()).isFalse();

    assertThat(response.customers()).hasSize(1);
    assertThat(response.customers().get(0).totalEstimatedMargin().getAmount())
        .isEqualByComparingTo("40.00");
    assertThat(response.customers().get(0).estimatedMarginPercentage())
        .isEqualByComparingTo("40.00");
    assertThat(response.customers().get(0).tradingPartnerName()).isEqualTo("Partner A");
    assertThat(response.customers().get(0).costIncomplete()).isFalse();
  }

  @Test
  void getEstimatedMargin_shouldFlagMissingCost() {
    // Arrange
    UUID orderId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();

    AnalyticsSalesOrderDto orderDto =
        AnalyticsSalesOrderDto.builder()
            .orderId(orderId)
            .orderNumber("SO-002")
            .quoteId(quoteId)
            .orderDate(LocalDate.now())
            .netRevenue(Money.of(new BigDecimal("100.00"), "USD"))
            .status(OrderStatus.CONFIRMED)
            .build();

    when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn(tenantCurrency);
    when(salesOrderPort.getOrdersForAnalytics(tenantId)).thenReturn(List.of(orderDto));
    when(costingPort.getEstimatedCostsByQuoteIds(tenantId, Set.of(quoteId))).thenReturn(Map.of());

    when(exchangeRateService.convert(
            eq(tenantId),
            eq(orderDto.netRevenue().getAmount()),
            eq(orderDto.netRevenue().getCurrency().getCurrencyCode()),
            eq(tenantCurrency),
            any()))
        .thenReturn(ConvertedMoney.sameUnit(new BigDecimal("100.00"), "USD"));

    // Act
    EstimatedMarginResponse response = estimatedMarginService.getEstimatedMargin();

    // Assert
    assertThat(response.orders()).hasSize(1);
    assertThat(response.orders().get(0).estimatedMargin()).isNull();
    assertThat(response.orders().get(0).warnings())
        .extracting(MarginWarningDto::code)
        .contains("MISSING_ESTIMATE");
  }

  @Test
  void getEstimatedMargin_shouldFlagIncompleteCost() {
    // Arrange
    UUID orderId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();

    AnalyticsSalesOrderDto orderDto =
        AnalyticsSalesOrderDto.builder()
            .orderId(orderId)
            .tradingPartnerId(partnerId)
            .quoteId(quoteId)
            .orderDate(LocalDate.now())
            .netRevenue(Money.of(new BigDecimal("100.00"), "USD"))
            .status(OrderStatus.CONFIRMED)
            .build();

    when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn(tenantCurrency);
    when(salesOrderPort.getOrdersForAnalytics(tenantId)).thenReturn(List.of(orderDto));

    AnalyticsCostEstimateDto costDto =
        AnalyticsCostEstimateDto.builder()
            .totalCost(Money.of(new BigDecimal("60.00"), "USD"))
            .complete(false)
            .build();

    when(costingPort.getEstimatedCostsByQuoteIds(tenantId, Set.of(quoteId)))
        .thenReturn(Map.of(quoteId, costDto));

    when(exchangeRateService.convert(any(), any(), any(), any(), any()))
        .thenAnswer(i -> ConvertedMoney.sameUnit(i.getArgument(1), i.getArgument(2)));

    // Act
    EstimatedMarginResponse response = estimatedMarginService.getEstimatedMargin();

    // Assert
    assertThat(response.orders().get(0).costIncomplete()).isTrue();
    assertThat(response.customers().get(0).costIncomplete()).isTrue();
  }
}
