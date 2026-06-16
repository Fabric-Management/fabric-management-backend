package com.fabricmanagement.analytics.app;

import com.fabricmanagement.analytics.dto.CustomerMarginDto;
import com.fabricmanagement.analytics.dto.EstimatedMarginResponse;
import com.fabricmanagement.analytics.dto.MarginWarningDto;
import com.fabricmanagement.analytics.dto.OrderMarginDto;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.app.port.AnalyticsCostingPort;
import com.fabricmanagement.costing.app.port.dto.AnalyticsCostEstimateDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.sales.salesorder.app.port.AnalyticsSalesOrderPort;
import com.fabricmanagement.sales.salesorder.app.port.dto.AnalyticsSalesOrderDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EstimatedMarginService {

  private final AnalyticsSalesOrderPort salesOrderPort;
  private final AnalyticsCostingPort costingPort;
  private final ExchangeRateService exchangeRateService;
  private final TradingPartnerResolver partnerResolver;
  private final TenantReportingCurrencyPort reportingCurrencyPort;

  public EstimatedMarginResponse getEstimatedMargin() {
    UUID tenantId = TenantContext.requireTenantId();
    String reportingCurrency = reportingCurrencyPort.getReportingCurrency(tenantId);
    LocalDate now = LocalDate.now();

    // 1. Fetch Orders
    List<AnalyticsSalesOrderDto> orders = salesOrderPort.getOrdersForAnalytics(tenantId);
    if (orders.isEmpty()) {
      return new EstimatedMarginResponse(List.of(), List.of(), reportingCurrency);
    }

    // 2. Fetch Costs
    Set<UUID> quoteIds =
        orders.stream()
            .map(AnalyticsSalesOrderDto::quoteId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, AnalyticsCostEstimateDto> costs =
        costingPort.getEstimatedCostsByQuoteIds(tenantId, quoteIds);

    // 3. Resolve Partner Names
    Set<UUID> partnerIds =
        orders.stream()
            .map(AnalyticsSalesOrderDto::tradingPartnerId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, String> partnerNames =
        partnerResolver.resolveDisplayNames(tenantId, new java.util.ArrayList<>(partnerIds));

    // 4. Compute Margins
    List<OrderMarginDto> orderMargins = new ArrayList<>();
    Map<UUID, CustomerAggregator> customerAggregators = new HashMap<>();

    for (AnalyticsSalesOrderDto order : orders) {
      List<MarginWarningDto> warnings = new ArrayList<>();
      boolean costIncomplete = false;

      // Revenue conversion
      var revenueResult =
          exchangeRateService.convert(
              tenantId,
              order.netRevenue().getAmount(),
              order.netRevenue().getCurrency().getCurrencyCode(),
              reportingCurrency,
              now);

      Money convertedRevenue = Money.of(revenueResult.getConvertedAmount(), reportingCurrency);

      // Cost resolution and conversion
      Money convertedCost = null;
      if (order.quoteId() == null) {
        warnings.add(
            new MarginWarningDto(
                "MISSING_ESTIMATE", order.orderId(), "No quote ID linked to order"));
      } else {
        AnalyticsCostEstimateDto costDto = costs.get(order.quoteId());
        if (costDto == null) {
          warnings.add(
              new MarginWarningDto(
                  "MISSING_ESTIMATE", order.orderId(), "No estimated cost found for quote"));
        } else {
          if (!costDto.complete()) {
            costIncomplete = true;
            warnings.add(
                new MarginWarningDto(
                    "COST_INCOMPLETE", order.orderId(), "Cost calculation is incomplete"));
          }
          var costResult =
              exchangeRateService.convert(
                  tenantId,
                  costDto.totalCost().getAmount(),
                  costDto.totalCost().getCurrency().getCurrencyCode(),
                  reportingCurrency,
                  now);
          convertedCost = Money.of(costResult.getConvertedAmount(), reportingCurrency);
        }
      }

      Money margin = null;
      BigDecimal marginPercentage = null;
      if (convertedCost != null) {
        margin = convertedRevenue.subtract(convertedCost);
        marginPercentage = calculatePercentage(margin, convertedRevenue);
      }

      OrderMarginDto orderMargin =
          OrderMarginDto.builder()
              .orderId(order.orderId())
              .orderNumber(order.orderNumber())
              .tradingPartnerId(order.tradingPartnerId())
              .quoteId(order.quoteId())
              .orderDate(order.orderDate())
              .revenue(convertedRevenue)
              .estimatedCost(convertedCost)
              .estimatedMargin(margin)
              .estimatedMarginPercentage(marginPercentage)
              .costIncomplete(costIncomplete)
              .warnings(warnings)
              .build();

      orderMargins.add(orderMargin);

      // Aggregate by customer
      if (order.tradingPartnerId() != null) {
        customerAggregators
            .computeIfAbsent(
                order.tradingPartnerId(), k -> new CustomerAggregator(reportingCurrency))
            .addOrder(orderMargin, warnings);
      }
    }

    // 5. Build Customer DTOs
    List<CustomerMarginDto> customerMargins =
        customerAggregators.entrySet().stream()
            .map(
                entry -> {
                  UUID partnerId = entry.getKey();
                  CustomerAggregator agg = entry.getValue();
                  return CustomerMarginDto.builder()
                      .tradingPartnerId(partnerId)
                      .tradingPartnerName(partnerNames.getOrDefault(partnerId, "Unknown"))
                      .totalRevenue(agg.totalRevenue)
                      .totalEstimatedCost(agg.totalCost)
                      .totalEstimatedMargin(agg.totalMargin)
                      .estimatedMarginPercentage(
                          calculatePercentage(agg.totalMargin, agg.totalRevenue))
                      .orderCount(agg.orderCount)
                      .costIncomplete(agg.costIncomplete)
                      .warnings(agg.warnings)
                      .build();
                })
            .toList();

    return new EstimatedMarginResponse(orderMargins, customerMargins, reportingCurrency);
  }

  private BigDecimal calculatePercentage(Money margin, Money revenue) {
    if (revenue == null || revenue.getAmount().compareTo(BigDecimal.ZERO) == 0 || margin == null) {
      return BigDecimal.ZERO;
    }
    return margin
        .getAmount()
        .divide(revenue.getAmount(), 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100));
  }

  private static class CustomerAggregator {
    Money totalRevenue;
    Money totalCost;
    Money totalMargin;
    int orderCount = 0;
    boolean costIncomplete = false;
    List<MarginWarningDto> warnings = new ArrayList<>();

    CustomerAggregator(String currency) {
      totalRevenue = Money.zero(currency);
      totalCost = Money.zero(currency);
      totalMargin = Money.zero(currency);
    }

    void addOrder(OrderMarginDto order, List<MarginWarningDto> orderWarnings) {
      orderCount++;
      if (order.revenue() != null) {
        totalRevenue = totalRevenue.add(order.revenue());
      }
      if (order.estimatedCost() != null) {
        totalCost = totalCost.add(order.estimatedCost());
      }
      if (order.estimatedMargin() != null) {
        totalMargin = totalMargin.add(order.estimatedMargin());
      }
      if (order.costIncomplete()) {
        costIncomplete = true;
      }
      warnings.addAll(orderWarnings);
    }
  }
}
