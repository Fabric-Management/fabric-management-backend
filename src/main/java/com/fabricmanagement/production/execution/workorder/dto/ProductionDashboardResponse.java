package com.fabricmanagement.production.execution.workorder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Schema(description = "Aggregate production dashboard for the current tenant")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductionDashboardResponse(
    @Schema(description = "WorkOrder count per status (all statuses included, 0 if none)")
        Map<String, Long> statusBreakdown,
    @Schema(description = "Active WO count (excludes COMPLETED and CANCELLED)") long activeCount,
    @Schema(description = "WOs past deadline that are not yet completed or cancelled")
        long overdueCount,
    @Schema(description = "Planned vs actual cost comparison") CostSummary costSummary,
    @Schema(description = "Yield performance for completed WOs. Null if no completions.")
        YieldSummary yieldSummary) {

  @Schema(description = "Cost variance between planned (unitCost × plannedQty) and actual cost")
  public record CostSummary(
      @Schema(description = "Sum of unitCost × plannedQty for non-cancelled WOs")
          BigDecimal totalPlannedCost,
      @Schema(description = "Sum of actualCost for completed WOs") BigDecimal totalActualCost,
      @Schema(description = "actualTotal - plannedTotal (negative = saving)")
          BigDecimal varianceAmount,
      @Schema(description = "OVERRUN | SAVING | ON_TARGET (±2%) | NO_DATA")
          String varianceDirection,
      String currency) {

    public static CostSummary of(BigDecimal totalPlanned, BigDecimal totalActual, String currency) {
      if (totalPlanned == null) totalPlanned = BigDecimal.ZERO;
      if (totalActual == null) totalActual = BigDecimal.ZERO;

      BigDecimal variance = totalActual.subtract(totalPlanned);
      String direction;
      if (totalPlanned.compareTo(BigDecimal.ZERO) == 0) {
        direction = "NO_DATA";
      } else {
        BigDecimal ratio = variance.abs().divide(totalPlanned, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("0.02")) <= 0) {
          direction = "ON_TARGET";
        } else {
          direction = variance.compareTo(BigDecimal.ZERO) > 0 ? "OVERRUN" : "SAVING";
        }
      }
      return new CostSummary(totalPlanned, totalActual, variance, direction, currency);
    }
  }

  @Schema(description = "Average yield and completed WO count")
  public record YieldSummary(
      @Schema(description = "Average yield percentage across completed WOs", example = "94.50")
          BigDecimal averageYieldPercentage,
      long completedCount) {}

  public static ProductionDashboardResponse of(
      Map<String, Long> statusBreakdown,
      long overdueCount,
      BigDecimal totalPlannedCost,
      BigDecimal totalActualCost,
      BigDecimal avgYield,
      long completedCount,
      String currency) {

    long activeCount =
        statusBreakdown.entrySet().stream()
            .filter(e -> !"COMPLETED".equals(e.getKey()) && !"CANCELLED".equals(e.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();

    CostSummary costSummary = CostSummary.of(totalPlannedCost, totalActualCost, currency);

    YieldSummary yieldSummary =
        completedCount > 0 ? new YieldSummary(avgYield, completedCount) : null;

    return new ProductionDashboardResponse(
        statusBreakdown, activeCount, overdueCount, costSummary, yieldSummary);
  }
}
