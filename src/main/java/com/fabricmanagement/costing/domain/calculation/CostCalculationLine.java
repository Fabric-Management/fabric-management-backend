package com.fabricmanagement.costing.domain.calculation;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

/**
 * A single cost breakdown line within a {@link CostCalculation}.
 *
 * <p>Each line represents one {@code costItemCode} contribution (e.g. "RAW_MATERIAL", "LABOR") with
 * its quantity, unit price, exchange rate, and resulting amount in the base currency.
 */
@Entity
@Table(name = "cost_calculation_line", schema = "costing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostCalculationLine extends BaseEntity {

  @Column(name = "cost_calculation_id", nullable = false)
  private UUID costCalculationId;

  /** References {@code costing.cost_item.code}. */
  @Column(name = "cost_item_code", nullable = false, length = 50)
  private String costItemCode;

  /** Quantity consumed or applied (e.g. kg, hours). Null for FIXED / PERCENTAGE items. */
  @Column(name = "qty", precision = 15, scale = 3)
  private BigDecimal qty;

  @Column(name = "unit", length = 20)
  private String unit;

  @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "currency", nullable = false, length = 10)
  @Builder.Default
  private String currency = "TRY";

  @Column(name = "total_in_base_currency", nullable = false, precision = 18, scale = 4)
  @Builder.Default
  private BigDecimal totalInBaseCurrency = BigDecimal.ZERO;

  /** Rate used to convert from line currency to base currency; null when currencies match. */
  @Column(name = "exchange_rate", precision = 20, scale = 8)
  private BigDecimal exchangeRate;

  @Column(name = "volume_discount_applied", nullable = false)
  @Builder.Default
  private boolean volumeDiscountApplied = false;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Override
  protected String getModuleCode() {
    return "CLINE";
  }
}
