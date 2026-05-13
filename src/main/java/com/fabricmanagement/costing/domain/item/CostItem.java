package com.fabricmanagement.costing.domain.item;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A cost item represents a single line of cost in a production calculation.
 *
 * <p>8 global items (RAW_PRODUCT, LABOR, MACHINE, ENERGY, OVERHEAD, LOGISTICS, QUALITY, PACKAGING)
 * are system-defined and seeded in migration. Module-specific items (e.g. FIBER_BALING) are also
 * system-defined; tenants cannot add rows.
 */
@Entity
@Table(name = "cost_item", schema = "costing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostItem extends BaseEntity {

  /** Business key — globally unique (e.g. "RAW_PRODUCT", "FIBER_BALING"). */
  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false, length = 30)
  private CostItemScope scope;

  /** Null for GLOBAL items; populated for MODULE_SPECIFIC items (e.g. "FIBER"). */
  @Column(name = "module_type", length = 50)
  private String moduleType;

  @Enumerated(EnumType.STRING)
  @Column(name = "calculation_base", nullable = false, length = 30)
  private CalculationBase calculationBase;

  @Column(name = "display_order")
  @Builder.Default
  private int displayOrder = 0;

  @Override
  protected String getModuleCode() {
    return "CITEM";
  }
}
