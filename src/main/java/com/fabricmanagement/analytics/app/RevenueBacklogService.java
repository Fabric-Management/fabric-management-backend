package com.fabricmanagement.analytics.app;

import com.fabricmanagement.analytics.dto.BacklogByCustomerDto;
import com.fabricmanagement.analytics.dto.RevenueBacklogResponse;
import com.fabricmanagement.analytics.dto.RevenueBacklogWarningDto;
import com.fabricmanagement.analytics.dto.RevenueTrendBucketDto;
import com.fabricmanagement.analytics.dto.RevenueTrendCustomerDto;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.finance.common.app.port.AnalyticsFinancePort;
import com.fabricmanagement.finance.common.app.port.dto.AnalyticsRevenueRecordDto;
import com.fabricmanagement.finance.common.app.port.dto.AnalyticsRevenueResponse;
import com.fabricmanagement.finance.common.dto.FinanceWarningDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.sales.salesorder.app.port.AnalyticsSalesOrderPort;
import com.fabricmanagement.sales.salesorder.app.port.dto.AnalyticsSalesOrderDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RevenueBacklogService {

  private static final int REPORTING_SCALE = 4;
  // REJECTED orders are not backlog. The port already filters out DRAFT and CANCELLED.
  private static final Set<String> EXCLUDED_BACKLOG_STATUSES = Set.of("DELIVERED", "REJECTED");

  private final AnalyticsFinancePort analyticsFinancePort;
  private final AnalyticsSalesOrderPort analyticsSalesOrderPort;
  private final TradingPartnerResolver tradingPartnerResolver;
  private final ExchangeRateService exchangeRateService;
  private final TenantReportingCurrencyPort reportingCurrencyPort;
  private final Clock clock;

  public RevenueBacklogResponse getTrends(int months) {
    UUID tenantId = TenantContext.requireTenantId();
    LocalDate today = LocalDate.now(clock);
    LocalDate fromDate = today.minusMonths(months - 1).withDayOfMonth(1);
    String reportingCurrency = reportingCurrencyPort.getReportingCurrency(tenantId);
    List<RevenueBacklogWarningDto> warnings = new ArrayList<>();

    // 1. Fetch Revenue Data
    AnalyticsRevenueResponse revenueResponse =
        analyticsFinancePort.getIssuedRevenueByCustomer(
            tenantId, fromDate, today, reportingCurrency);

    if (revenueResponse.warnings() != null) {
      for (FinanceWarningDto w : revenueResponse.warnings()) {
        warnings.add(
            new RevenueBacklogWarningDto(
                w.code(), w.invoiceId() != null ? w.invoiceId().toString() : null, w.message()));
      }
    }

    // 2. Group Revenue by Bucket and Customer
    Map<YearMonth, Map<UUID, BigDecimal>> revenueBuckets = new HashMap<>();
    List<UUID> allPartnerIds = new ArrayList<>();

    if (revenueResponse.records() != null) {
      for (AnalyticsRevenueRecordDto record : revenueResponse.records()) {
        YearMonth ym = YearMonth.from(record.issueDate());
        if (!revenueBuckets.containsKey(ym)) {
          revenueBuckets.put(ym, new HashMap<>());
        }
        revenueBuckets
            .get(ym)
            .merge(record.customerId(), record.reportingAmount(), BigDecimal::add);
        allPartnerIds.add(record.customerId());
      }
    }

    // 3. Fetch Backlog Data
    List<AnalyticsSalesOrderDto> allOrders =
        analyticsSalesOrderPort.getOrdersForAnalytics(tenantId);
    Map<UUID, BacklogAggregator> backlogAggregators = new HashMap<>();

    for (AnalyticsSalesOrderDto order : allOrders) {
      if (!EXCLUDED_BACKLOG_STATUSES.contains(order.status())) {
        allPartnerIds.add(order.tradingPartnerId());

        BigDecimal convertedValue;
        try {
          convertedValue =
              exchangeRateService
                  .convert(
                      tenantId,
                      order.netRevenue().getAmount(),
                      order.netRevenue().getCurrency().getCurrencyCode(),
                      reportingCurrency,
                      today)
                  .getConvertedAmount();
        } catch (ExchangeRateRequiredException ex) {
          convertedValue = order.netRevenue().getAmount(); // Degrade
          warnings.add(
              new RevenueBacklogWarningDto(
                  "MISSING_EXCHANGE_RATE",
                  order.orderId().toString(),
                  "Missing exchange rate for order backlog conversion: "
                      + order.netRevenue().getCurrency().getCurrencyCode()
                      + " to "
                      + reportingCurrency
                      + " \u2014 using raw "
                      + order.netRevenue().getCurrency().getCurrencyCode()
                      + " amount as fallback"));
        }

        backlogAggregators
            .computeIfAbsent(order.tradingPartnerId(), k -> new BacklogAggregator())
            .add(convertedValue);
      }
    }

    // 4. Resolve Partner Names
    Map<UUID, String> partnerNames =
        tradingPartnerResolver.resolveDisplayNames(
            tenantId, allPartnerIds.stream().distinct().toList());

    // 5. Build Revenue Trend DTOs
    List<RevenueTrendBucketDto> revenueTrend = new ArrayList<>();
    DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MMM yyyy");

    YearMonth currentBucket = YearMonth.from(fromDate);
    YearMonth endBucket = YearMonth.from(today);

    while (!currentBucket.isAfter(endBucket)) {
      Map<UUID, BigDecimal> customerAmounts = revenueBuckets.getOrDefault(currentBucket, Map.of());
      BigDecimal bucketTotal = BigDecimal.ZERO;
      List<RevenueTrendCustomerDto> byCustomer = new ArrayList<>();

      for (Map.Entry<UUID, BigDecimal> entry : customerAmounts.entrySet()) {
        BigDecimal amt = entry.getValue().setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
        bucketTotal = bucketTotal.add(amt);
        byCustomer.add(
            new RevenueTrendCustomerDto(
                entry.getKey(), partnerNames.getOrDefault(entry.getKey(), "Unknown Partner"), amt));
      }

      // Sort customers by amount descending
      byCustomer.sort(Comparator.comparing(RevenueTrendCustomerDto::amount).reversed());

      revenueTrend.add(
          new RevenueTrendBucketDto(
              currentBucket.toString(),
              currentBucket.format(labelFormatter),
              currentBucket.atDay(1),
              currentBucket.atEndOfMonth(),
              bucketTotal.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
              byCustomer));

      currentBucket = currentBucket.plusMonths(1);
    }

    // 6. Build Backlog DTOs
    List<BacklogByCustomerDto> backlogByCustomer = new ArrayList<>();
    for (Map.Entry<UUID, BacklogAggregator> entry : backlogAggregators.entrySet()) {
      backlogByCustomer.add(
          new BacklogByCustomerDto(
              entry.getKey(),
              partnerNames.getOrDefault(entry.getKey(), "Unknown Partner"),
              entry.getValue().totalValue().setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
              entry.getValue().count()));
    }

    // Sort backlog descending
    backlogByCustomer.sort(
        Comparator.comparing(BacklogByCustomerDto::committedOrderValue).reversed());

    return new RevenueBacklogResponse(revenueTrend, backlogByCustomer, reportingCurrency, warnings);
  }

  private static class BacklogAggregator {
    private BigDecimal totalValue = BigDecimal.ZERO;
    private int count = 0;

    void add(BigDecimal value) {
      this.totalValue = this.totalValue.add(value);
      this.count++;
    }

    BigDecimal totalValue() {
      return totalValue;
    }

    int count() {
      return count;
    }
  }
}
