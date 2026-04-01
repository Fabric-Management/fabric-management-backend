package com.fabricmanagement.costing.dto;

import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.calculation.CostCalculationLine;
import com.fabricmanagement.costing.domain.calculation.CostStage;
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
 * Full cost report for a WorkOrder: PLANNED vs ACTUAL with per-material breakdown.
 *
 * <p>All nullable sections ({@code planned}, {@code actual}, {@code variance}, {@code
 * rawMaterialBreakdown}) are omitted from JSON when null — partial results are valid when only one
 * stage has been calculated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkOrderCostReportResponse(
    UUID workOrderId,
    CostStageDetail planned,
    CostStageDetail actual,
    VarianceSummary variance,
    List<MaterialCostBreakdown> rawMaterialBreakdown) {

  // ── Inner Records ────────────────────────────────────────────

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CostStageDetail(
      CostStage stage,
      BigDecimal totalCost,
      String currency,
      Instant calculatedAt,
      List<CostLineDetail> lines) {

    public static CostStageDetail from(CostCalculation calc) {
      List<CostLineDetail> lines =
          calc.getLines().stream()
              .sorted(Comparator.comparing(CostCalculationLine::getCostItemCode))
              .map(CostLineDetail::from)
              .toList();
      return new CostStageDetail(
          calc.getStage(), calc.getTotalCost(), calc.getCurrency(), calc.getCalculatedAt(), lines);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CostLineDetail(
      String costItemCode,
      UUID materialId,
      BigDecimal qty,
      String unit,
      BigDecimal unitPrice,
      String currency,
      BigDecimal totalInBaseCurrency,
      boolean volumeDiscountApplied) {

    public static CostLineDetail from(CostCalculationLine line) {
      return new CostLineDetail(
          line.getCostItemCode(),
          line.getMaterialId(),
          line.getQty(),
          line.getUnit(),
          line.getUnitPrice(),
          line.getCurrency(),
          line.getTotalInBaseCurrency(),
          line.isVolumeDiscountApplied());
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record VarianceSummary(
      BigDecimal amount, BigDecimal ratio, String direction, String currency) {

    private static final BigDecimal ON_TARGET_THRESHOLD = new BigDecimal("0.02");

    public static VarianceSummary between(CostCalculation planned, CostCalculation actual) {
      if (planned == null || actual == null) return null;
      if (planned.getTotalCost().compareTo(BigDecimal.ZERO) == 0) return null;

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

  public record MaterialCostBreakdown(
      UUID materialId,
      BigDecimal totalCost,
      BigDecimal totalWeight,
      String unit,
      String currency) {}

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

    List<MaterialCostBreakdown> rawMaterialBreakdown =
        actualOpt.map(WorkOrderCostReportResponse::buildMaterialBreakdown).orElse(null);

    return new WorkOrderCostReportResponse(
        workOrderId, plannedDetail, actualDetail, variance, rawMaterialBreakdown);
  }

  /**
   * Groups ACTUAL RAW_MATERIAL lines by materialId and aggregates cost + weight. Lines without
   * materialId (legacy/non-raw-material) are excluded from breakdown.
   */
  private static List<MaterialCostBreakdown> buildMaterialBreakdown(CostCalculation actual) {
    Map<UUID, List<CostCalculationLine>> byMaterial =
        actual.getLines().stream()
            .filter(l -> "RAW_MATERIAL".equals(l.getCostItemCode()))
            .filter(l -> l.getMaterialId() != null)
            .collect(Collectors.groupingBy(CostCalculationLine::getMaterialId));

    if (byMaterial.isEmpty()) return null;

    return byMaterial.entrySet().stream()
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
              String currency = lines.get(0).getCurrency();
              return new MaterialCostBreakdown(
                  entry.getKey(), totalCost, totalWeight, unit, currency);
            })
        .sorted(Comparator.comparing(MaterialCostBreakdown::totalCost).reversed())
        .toList();
  }
}
