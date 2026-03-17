package com.fabricmanagement.costing.domain.calculation;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;

/**
 * Root aggregate for a multi-line cost calculation result.
 *
 * <p>Polymorphic via {@code entityType + entityId}:
 *
 * <ul>
 *   <li>QUOTE — ESTIMATED stage; populates Quote.estimatedUnitCost
 *   <li>WORK_ORDER — PLANNED stage; populates WorkOrder.plannedCost
 *   <li>BATCH — ACTUAL stage; populates Batch.actualCost
 * </ul>
 *
 * <p>Variance detection: when the difference between the previous stage's {@code totalCost} and
 * this stage's {@code totalCost} exceeds a configured threshold, a {@link
 * com.fabricmanagement.costing.domain.event.CostVarianceDetectedEvent} is published.
 */
@Entity
@Table(name = "cost_calculation", schema = "costing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostCalculation extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 30)
  private CostEntityType entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "module_type", nullable = false, length = 50)
  private String moduleType;

  @Column(name = "cost_template_id")
  private UUID costTemplateId;

  @Enumerated(EnumType.STRING)
  @Column(name = "stage", nullable = false, length = 20)
  private CostStage stage;

  @Column(name = "total_cost", nullable = false, precision = 18, scale = 4)
  @Builder.Default
  private BigDecimal totalCost = BigDecimal.ZERO;

  @Column(name = "currency", nullable = false, length = 10)
  @Builder.Default
  private String currency = "TRY";

  @Column(name = "calculated_at", nullable = false)
  @Builder.Default
  private Instant calculatedAt = Instant.now();

  @Column(name = "exchange_rate_snapshot_id")
  private UUID exchangeRateSnapshotId;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @JoinColumn(name = "cost_calculation_id")
  @Builder.Default
  private List<CostCalculationLine> lines = new ArrayList<>();

  /**
   * Factory method — creates a new calculation shell. Lines are added separately via {@link
   * #addLine(CostCalculationLine)}.
   */
  public static CostCalculation create(
      UUID tenantId,
      CostEntityType entityType,
      UUID entityId,
      String moduleType,
      CostStage stage,
      String currency) {
    var calc = new CostCalculation();
    calc.setTenantId(tenantId);
    calc.setEntityType(entityType);
    calc.setEntityId(entityId);
    calc.setModuleType(moduleType);
    calc.setStage(stage);
    calc.setCurrency(currency != null ? currency : "TRY");
    calc.setCalculatedAt(Instant.now());
    calc.setTotalCost(BigDecimal.ZERO);
    calc.onCreate();
    return calc;
  }

  /** Add a line and recompute totalCost. */
  public void addLine(CostCalculationLine line) {
    lines.add(line);
    this.totalCost =
        lines.stream()
            .map(CostCalculationLine::getTotalInBaseCurrency)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    onUpdate();
  }

  /** Compute variance ratio vs. a previous stage's total. Returns 0 if previousTotal is zero. */
  public BigDecimal varianceRatioVs(BigDecimal previousTotal) {
    if (previousTotal == null || previousTotal.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return totalCost
        .subtract(previousTotal)
        .divide(previousTotal, 4, java.math.RoundingMode.HALF_UP);
  }

  @Override
  protected String getModuleCode() {
    return "CCALC";
  }
}
