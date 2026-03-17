package com.fabricmanagement.order.sales.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

/**
 * A single product line within a SalesOrder.
 *
 * <p>Each line may reference a {@code Material} entity (via materialId) or use a free-text {@code
 * productDesc}. At least one must be non-null (validated in service layer).
 *
 * <p>On {@code SalesOrderConfirmed}, the RuleEngine will:
 *
 * <ol>
 *   <li>Run 4-step recipe matching cascade.
 *   <li>Create a WorkOrder (DRAFT) linked to this line via {@code salesOrderLineId}.
 *   <li>Update {@code lineStatus} to RECIPE_ASSIGNED or leave PENDING with FlowBoard task.
 * </ol>
 *
 * <p>Table: {@code order.sales_order_line}
 */
@Entity
@Table(
    name = "sales_order_line",
    schema = "order",
    indexes = {
      @Index(name = "idx_sol_sales_order_id", columnList = "sales_order_id"),
      @Index(name = "idx_sol_material_id", columnList = "material_id"),
      @Index(name = "idx_sol_line_status", columnList = "line_status"),
      @Index(name = "idx_sol_recipe_id", columnList = "recipe_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SalesOrderLine extends BaseEntity {

  // ── References ───────────────────────────────────────────────────────────

  /** FK → SalesOrder. */
  @Column(name = "sales_order_id", nullable = false)
  private UUID salesOrderId;

  /**
   * FK → Material. Optional — use productDesc when material is not yet catalogued. At least one of
   * materialId / productDesc must be non-null.
   */
  @Column(name = "material_id")
  private UUID materialId;

  /**
   * Free-text product description. Used when no Material entity exists yet (custom / prototype
   * orders).
   */
  @Column(name = "product_desc", columnDefinition = "TEXT")
  private String productDesc;

  // ── Quantities & Pricing ─────────────────────────────────────────────────

  @Column(name = "requested_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal requestedQty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "unit_price", precision = 18, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "currency", length = 3)
  private String currency;

  // ── Module-specific specs ─────────────────────────────────────────────────

  /**
   * Module type (FIBER / YARN / FABRIC / DYE_FINISHING). Determines which JSONB schema applies to
   * moduleSpecs.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "module_type", length = 20)
  private ModuleType moduleType;

  /**
   * Module-specific specs as JSONB (certification, origin, weight, etc.). Schema varies by
   * moduleType — see architecture doc 03-sales/sales-order.md.
   */
  @Type(JsonType.class)
  @Column(name = "module_specs", columnDefinition = "jsonb")
  private Map<String, Object> moduleSpecs;

  // ── Status & Recipe ───────────────────────────────────────────────────────

  @Enumerated(EnumType.STRING)
  @Column(name = "line_status", nullable = false, length = 25)
  @Builder.Default
  private SalesOrderLineStatus lineStatus = SalesOrderLineStatus.PENDING;

  /**
   * Recipe assigned by RuleEngine or manually. Nullable — populated after RECIPE_ASSIGNED
   * transition.
   */
  @Column(name = "recipe_id")
  private UUID recipeId;

  @Override
  protected String getModuleCode() {
    return "SOL";
  }

  // ── Domain Methods ────────────────────────────────────────────────────────

  /** Assigns a recipe and transitions status to RECIPE_ASSIGNED. */
  public void assignRecipe(UUID recipeId) {
    if (!this.lineStatus.canTransitionTo(SalesOrderLineStatus.RECIPE_ASSIGNED)) {
      throw new IllegalStateException(
          String.format("Cannot assign recipe: line is in status %s", this.lineStatus));
    }
    this.recipeId = recipeId;
    this.lineStatus = SalesOrderLineStatus.RECIPE_ASSIGNED;
  }

  /** Validates that at least one of materialId / productDesc is present. */
  public boolean isValid() {
    return materialId != null || (productDesc != null && !productDesc.isBlank());
  }
}
