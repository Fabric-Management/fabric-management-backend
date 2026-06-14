package com.fabricmanagement.costing.dto;

import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.calculation.CostCalculationLine;
import com.fabricmanagement.costing.domain.calculation.CostStage;
import com.fabricmanagement.costing.domain.calculation.MissingCostItemEntry;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Full cost report for a WorkOrder: PLANNED vs ACTUAL with per-product breakdown.
 *
 * <p>All nullable sections ({@code planned}, {@code actual}, {@code variance}, {@code
 * rawProductBreakdown}) are omitted from JSON when null — partial results are valid when only one
 * stage has been calculated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkOrderCostReportResponse(
    UUID workOrderId,
    CostStageDetail planned,
    CostStageDetail actual,
    VarianceSummary variance,
    List<ProductCostBreakdown> rawProductBreakdown) {

  // ── Inner Records ────────────────────────────────────────────

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CostStageDetail(
      CostStage stage,
      BigDecimal totalCost,
      String currency,
      Instant calculatedAt,
      boolean complete,
      List<MissingCostItemEntry> missingItems,
      List<CostLineDetail> lines) {

    public static CostStageDetail from(CostCalculation calc) {
      List<CostLineDetail> lines =
          calc.getLines().stream()
              .sorted(Comparator.comparing(CostCalculationLine::getCostItemCode))
              .map(CostLineDetail::from)
              .toList();
      return new CostStageDetail(
          calc.getStage(),
          calc.getTotalCost(),
          calc.getCurrency(),
          calc.getCalculatedAt(),
          calc.isComplete(),
          calc.getMissingItems(),
          lines);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CostLineDetail(
      String costItemCode,
      UUID productId,
      BigDecimal qty,
      String unit,
      BigDecimal unitPrice,
      String priceCurrency,
      BigDecimal totalInReportingCurrency,
      String reportingCurrency,
      boolean volumeDiscountApplied,
      BigDecimal originalLineTotal,
      BigDecimal exchangeRateUsed,
      String exchangeRateDate) {

    public static CostLineDetail from(CostCalculationLine line) {
      String priceCur = line.getCurrency();
      String reportCur = line.getCurrency();
      BigDecimal origTotal = line.getTotalInBaseCurrency();
      BigDecimal exchRate = null;
      String exchDate = null;

      if (line.getConvertedTotal() != null) {
        priceCur = line.getConvertedTotal().getOriginalCurrency();
        reportCur = line.getConvertedTotal().getConvertedCurrency();
        origTotal = line.getConvertedTotal().getOriginalAmount();
        exchRate = line.getConvertedTotal().getExchangeRate();
        exchDate =
            line.getConvertedTotal().getRateDate() != null
                ? line.getConvertedTotal().getRateDate().toString()
                : null;
      }

      return new CostLineDetail(
          line.getCostItemCode(),
          line.getProductId(),
          line.getQty(),
          line.getUnit(),
          line.getUnitPrice(),
          priceCur,
          line.getTotalInBaseCurrency(),
          reportCur,
          line.isVolumeDiscountApplied(),
          origTotal,
          exchRate,
          exchDate);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record VarianceSummary(
      BigDecimal amount, BigDecimal ratio, String direction, String currency) {

    private static final BigDecimal ON_TARGET_THRESHOLD = new BigDecimal("0.02");

    public static VarianceSummary between(CostCalculation planned, CostCalculation actual) {
      if (planned == null || actual == null) return null;
      if (planned.getTotalCost().compareTo(BigDecimal.ZERO) == 0) return null;
      // Guard: comparing costs in different currencies is meaningless
      if (!planned.getCurrency().equals(actual.getCurrency())) return null;

      BigDecimal amount = actual.getTotalCost().subtract(planned.getTotalCost());
      BigDecimal ratio = amount.divide(planned.getTotalCost(), 4, RoundingMode.HALF_UP);

      String direction;
      BigDecimal absRatio = ratio.abs();
      if (absRatio.compareTo(ON_TARGET_THRESHOLD) <= 0) {
        direction = "ON_TARGET";
      } else {
        direction = amount.compareTo(BigDecimal.ZERO) > 0 ? "OVERRUN" : "SAVING";
      }

      return new VarianceSummary(amount, ratio, direction, planned.getCurrency());
    }
  }

  public record ProductCostBreakdown(
      UUID productId, BigDecimal totalCost, BigDecimal totalWeight, String unit, String currency) {}

  // ── Factory ──────────────────────────────────────────────────

  /**
   * Builds the report from optional PLANNED and ACTUAL calculations.
   *
   * <p>Either or both may be null (stage not yet calculated or failed).
   */
  public static WorkOrderCostReportResponse of(
      UUID workOrderId, Optional<CostCalculation> plannedOpt, Optional<CostCalculation> actualOpt) {

    CostStageDetail plannedDetail = plannedOpt.map(CostStageDetail::from).orElse(null);

    CostStageDetail actualDetail = actualOpt.map(CostStageDetail::from).orElse(null);

    VarianceSummary variance =
        VarianceSummary.between(plannedOpt.orElse(null), actualOpt.orElse(null));

    List<ProductCostBreakdown> rawProductBreakdown =
        actualOpt.map(WorkOrderCostReportResponse::buildProductBreakdown).orElse(null);

    return new WorkOrderCostReportResponse(
        workOrderId, plannedDetail, actualDetail, variance, rawProductBreakdown);
  }

  /**
   * Groups ACTUAL RAW_PRODUCT lines by productId and aggregates cost + weight. Lines without
   * productId (legacy/non-raw-product) are excluded from breakdown.
   */
  private static List<ProductCostBreakdown> buildProductBreakdown(CostCalculation actual) {
    Map<UUID, List<CostCalculationLine>> byProduct =
        actual.getLines().stream()
            .filter(l -> "RAW_PRODUCT".equals(l.getCostItemCode()))
            .filter(l -> l.getProductId() != null)
            .collect(Collectors.groupingBy(CostCalculationLine::getProductId));

    if (byProduct.isEmpty()) return null;

    return byProduct.entrySet().stream()
        .map(
            entry -> {
              List<CostCalculationLine> lines = entry.getValue();
              BigDecimal totalCost =
                  lines.stream()
                      .map(CostCalculationLine::getTotalInBaseCurrency)
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              BigDecimal totalWeight =
                  lines.stream()
                      .map(l -> l.getQty() != null ? l.getQty() : BigDecimal.ZERO)
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              String unit = lines.get(0).getUnit();
              // Use reporting currency (matches totalInBaseCurrency which is in reporting currency)
              String currency =
                  lines.get(0).getConvertedTotal() != null
                      ? lines.get(0).getConvertedTotal().getConvertedCurrency()
                      : lines.get(0).getCurrency();
              return new ProductCostBreakdown(
                  entry.getKey(), totalCost, totalWeight, unit, currency);
            })
        .sorted(Comparator.comparing(ProductCostBreakdown::totalCost).reversed())
        .toList();
  }
}
