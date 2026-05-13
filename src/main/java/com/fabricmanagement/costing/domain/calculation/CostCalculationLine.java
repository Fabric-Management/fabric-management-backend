package com.fabricmanagement.costing.domain.calculation;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

/**
 * A single cost breakdown line within a {@link CostCalculation}.
 *
 * <p>Each line represents one {@code costItemCode} contribution (e.g. "RAW_PRODUCT", "LABOR") with
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

  /**
   * @deprecated Since Sprint 7b — exchange rate is now stored in the embedded {@code
   *     convertedTotal.exchangeRate} field. Retained for backward compatibility with pre-Sprint 7b
   *     data. Will be removed in a future migration.
   */
  @Deprecated(since = "Sprint 7b", forRemoval = true)
  @Column(name = "exchange_rate", precision = 20, scale = 8)
  private BigDecimal exchangeRate;

  @Column(name = "volume_discount_applied", nullable = false)
  @Builder.Default
  private boolean volumeDiscountApplied = false;

  /**
   * The specific product this cost line applies to. Populated for RAW_PRODUCT lines in
   * multi-product WorkOrder cost calculations (Sprint 6+). Null for non-product cost items (LABOR,
   * OVERHEAD, etc.) and legacy single-product calculations.
   */
  @Column(name = "product_id")
  private UUID productId;

  // Sprint 7b — Multi-Currency: conversion audit trail
  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "originalAmount", column = @Column(name = "original_line_total")),
    @AttributeOverride(name = "originalCurrency", column = @Column(name = "original_currency")),
    @AttributeOverride(name = "convertedAmount", column = @Column(name = "converted_line_total")),
    @AttributeOverride(name = "convertedCurrency", column = @Column(name = "reporting_currency")),
    @AttributeOverride(name = "exchangeRate", column = @Column(name = "exchange_rate_used")),
    @AttributeOverride(name = "rateDate", column = @Column(name = "exchange_rate_date"))
  })
  private com.fabricmanagement.common.domain.vo.ConvertedMoney convertedTotal;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Override
  protected String getModuleCode() {
    return "CLINE";
  }
}
