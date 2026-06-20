package com.fabricmanagement.analytics.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.analytics.dto.RevenueBacklogResponse;
import com.fabricmanagement.analytics.dto.RevenueTrendBucketDto;
import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.finance.common.app.port.AnalyticsFinancePort;
import com.fabricmanagement.finance.common.app.port.dto.AnalyticsRevenueRecordDto;
import com.fabricmanagement.finance.common.app.port.dto.AnalyticsRevenueResponse;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.sales.salesorder.app.port.AnalyticsSalesOrderPort;
import com.fabricmanagement.sales.salesorder.app.port.dto.AnalyticsSalesOrderDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevenueBacklogServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_1 = UUID.randomUUID();
  private static final UUID CUSTOMER_2 = UUID.randomUUID();

  @Mock private AnalyticsFinancePort analyticsFinancePort;
  @Mock private AnalyticsSalesOrderPort analyticsSalesOrderPort;
  @Mock private TradingPartnerResolver tradingPartnerResolver;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;

  private Clock clock;
  private RevenueBacklogService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    // Fixed clock to 2024-06-15
    clock = Clock.fixed(Instant.parse("2024-06-15T10:00:00Z"), ZoneId.of("UTC"));
    service =
        new RevenueBacklogService(
            analyticsFinancePort,
            analyticsSalesOrderPort,
            tradingPartnerResolver,
            exchangeRateService,
            reportingCurrencyPort,
            clock);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldReturnEmptyTrendsWhenNoData() {
    when(reportingCurrencyPort.getReportingCurrency(TENANT_ID)).thenReturn("USD");
    when(analyticsFinancePort.getIssuedRevenueByCustomer(eq(TENANT_ID), any(), any(), eq("USD")))
        .thenReturn(new AnalyticsRevenueResponse(List.of(), List.of()));
    when(analyticsSalesOrderPort.getOrdersForAnalytics(TENANT_ID)).thenReturn(List.of());
    when(tradingPartnerResolver.resolveDisplayNames(eq(TENANT_ID), any())).thenReturn(Map.of());

    RevenueBacklogResponse response = service.getTrends(3);

    assertThat(response.reportingCurrency()).isEqualTo("USD");
    assertThat(response.revenueTrend()).hasSize(3); // 3 empty buckets
    assertThat(response.backlogByCustomer()).isEmpty();
    assertThat(response.warnings()).isEmpty();
  }

  @Test
  void shouldGroupRevenueByMonthAndCustomer() {
    when(reportingCurrencyPort.getReportingCurrency(TENANT_ID)).thenReturn("USD");

    LocalDate today = LocalDate.now(clock); // 2024-06-15
    LocalDate fromDate = today.minusMonths(2).withDayOfMonth(1); // 2024-04-01

    List<AnalyticsRevenueRecordDto> records =
        List.of(
            AnalyticsRevenueRecordDto.builder()
                .customerId(CUSTOMER_1)
                .issueDate(LocalDate.of(2024, 4, 10))
                .reportingAmount(new BigDecimal("100"))
                .build(),
            AnalyticsRevenueRecordDto.builder()
                .customerId(CUSTOMER_1)
                .issueDate(LocalDate.of(2024, 4, 20))
                .reportingAmount(new BigDecimal("50"))
                .build(),
            AnalyticsRevenueRecordDto.builder()
                .customerId(CUSTOMER_2)
                .issueDate(LocalDate.of(2024, 6, 5))
                .reportingAmount(new BigDecimal("200"))
                .build());

    when(analyticsFinancePort.getIssuedRevenueByCustomer(TENANT_ID, fromDate, today, "USD"))
        .thenReturn(new AnalyticsRevenueResponse(records, List.of()));

    when(analyticsSalesOrderPort.getOrdersForAnalytics(TENANT_ID)).thenReturn(List.of());
    when(tradingPartnerResolver.resolveDisplayNames(TENANT_ID, List.of(CUSTOMER_1, CUSTOMER_2)))
        .thenReturn(Map.of(CUSTOMER_1, "Cust A", CUSTOMER_2, "Cust B"));

    RevenueBacklogResponse response = service.getTrends(3);

    assertThat(response.revenueTrend()).hasSize(3);

    RevenueTrendBucketDto apr = response.revenueTrend().get(0);
    assertThat(apr.key()).isEqualTo("2024-04");
    assertThat(apr.totalAmount()).isEqualByComparingTo("150");
    assertThat(apr.byCustomer()).hasSize(1);
    assertThat(apr.byCustomer().get(0).customerId()).isEqualTo(CUSTOMER_1);
    assertThat(apr.byCustomer().get(0).amount()).isEqualByComparingTo("150");

    RevenueTrendBucketDto may = response.revenueTrend().get(1);
    assertThat(may.key()).isEqualTo("2024-05");
    assertThat(may.totalAmount()).isEqualByComparingTo("0");

    RevenueTrendBucketDto jun = response.revenueTrend().get(2);
    assertThat(jun.key()).isEqualTo("2024-06");
    assertThat(jun.totalAmount()).isEqualByComparingTo("200");
  }

  @Test
  void shouldCalculateBacklogAndDegradeOnMissingFx() {
    when(reportingCurrencyPort.getReportingCurrency(TENANT_ID)).thenReturn("USD");

    when(analyticsFinancePort.getIssuedRevenueByCustomer(any(), any(), any(), any()))
        .thenReturn(new AnalyticsRevenueResponse(List.of(), List.of()));

    List<AnalyticsSalesOrderDto> orders =
        List.of(
            // Included
            AnalyticsSalesOrderDto.builder()
                .orderId(UUID.randomUUID())
                .tradingPartnerId(CUSTOMER_1)
                .netRevenue(Money.of(new BigDecimal("100"), "USD"))
                .status("CONFIRMED")
                .build(),
            // Needs FX conversion
            AnalyticsSalesOrderDto.builder()
                .orderId(UUID.randomUUID())
                .tradingPartnerId(CUSTOMER_2)
                .netRevenue(Money.of(new BigDecimal("2000"), "EUR"))
                .status("IN_PROGRESS")
                .build(),
            // Excluded (Delivered)
            AnalyticsSalesOrderDto.builder()
                .orderId(UUID.randomUUID())
                .tradingPartnerId(CUSTOMER_1)
                .netRevenue(Money.of(new BigDecimal("1000"), "USD"))
                .status("DELIVERED")
                .build());

    when(analyticsSalesOrderPort.getOrdersForAnalytics(TENANT_ID)).thenReturn(orders);
    when(tradingPartnerResolver.resolveDisplayNames(TENANT_ID, List.of(CUSTOMER_1, CUSTOMER_2)))
        .thenReturn(Map.of(CUSTOMER_1, "Cust A", CUSTOMER_2, "Cust B"));

    // Mock FX for USD -> USD (mock it out as throwing for EUR to test degrade)
    when(exchangeRateService.convert(
            eq(TENANT_ID), any(BigDecimal.class), eq("USD"), eq("USD"), any()))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("100"),
                "USD",
                new BigDecimal("100"),
                "USD",
                BigDecimal.ONE,
                LocalDate.now(clock)));

    when(exchangeRateService.convert(
            eq(TENANT_ID), any(BigDecimal.class), eq("EUR"), eq("USD"), any()))
        .thenThrow(new ExchangeRateRequiredException("EUR", "USD", LocalDate.now(clock)));

    RevenueBacklogResponse response = service.getTrends(1);

    assertThat(response.backlogByCustomer()).hasSize(2);
    // Cust B: 2000 (degraded)
    assertThat(response.backlogByCustomer().get(0).customerId()).isEqualTo(CUSTOMER_2);
    assertThat(response.backlogByCustomer().get(0).committedOrderValue())
        .isEqualByComparingTo("2000");
    assertThat(response.backlogByCustomer().get(0).orderCount()).isEqualTo(1);

    // Cust A: 100
    assertThat(response.backlogByCustomer().get(1).customerId()).isEqualTo(CUSTOMER_1);
    assertThat(response.backlogByCustomer().get(1).committedOrderValue())
        .isEqualByComparingTo("100");
    assertThat(response.backlogByCustomer().get(1).orderCount()).isEqualTo(1);

    // Check warnings for FX
    assertThat(response.warnings()).hasSize(1);
    assertThat(response.warnings().get(0).code()).isEqualTo("MISSING_EXCHANGE_RATE");
  }
}
