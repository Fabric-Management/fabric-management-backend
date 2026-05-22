package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.util.Money;
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
 * <p>Each line may reference a {@code Product} entity (via productId) or use a free-text {@code
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
 * <p>Table: {@code sales_ord.sales_order_line}
 */
@Entity
@Table(
    name = "sales_order_line",
    schema = "sales_ord",
    indexes = {
      @Index(name = "idx_sol_sales_order_id", columnList = "sales_order_id"),
      @Index(name = "idx_sol_product_id", columnList = "product_id"),
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
   * FK → Product. Optional — use productDesc when product is not yet catalogued. At least one of
   * productId / productDesc must be non-null.
   */
  @Column(name = "product_id")
  private UUID productId;

  /**
   * Free-text product description. Used when no Product entity exists yet (custom / prototype
   * orders).
   */
  @Column(name = "product_desc", columnDefinition = "TEXT")
  private String productDesc;

  // ── Quantities & Pricing ─────────────────────────────────────────────────

  @Column(name = "requested_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal requestedQty;

  @Column(name = "shipped_qty", nullable = false, precision = 15, scale = 3)
  @Builder.Default
  private BigDecimal shippedQty = BigDecimal.ZERO;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "unit_price", precision = 18, scale = 4)),
    @AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3))
  })
  private Money unitPrice;

  public String getCurrency() {
    return unitPrice != null && unitPrice.getCurrency() != null
        ? unitPrice.getCurrency().getCurrencyCode()
        : null;
  }

  @ElementCollection
  @CollectionTable(
      name = "sales_order_line_processed_shipments",
      schema = "sales_ord",
      joinColumns = @JoinColumn(name = "sales_order_line_id"))
  @Column(name = "shipment_line_id", nullable = false)
  @Builder.Default
  private java.util.Set<UUID> processedShipmentLineIds = new java.util.HashSet<>();

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
      throw new com.fabricmanagement.sales.common.exception.OrderDomainException(
          String.format(
              "Cannot assign recipe to SalesOrderLine %s: current status %s does not allow recipe assignment",
              this.getId(), this.lineStatus));
    }
    this.recipeId = recipeId;
    this.lineStatus = SalesOrderLineStatus.RECIPE_ASSIGNED;
  }

  /** Validates that at least one of productId / productDesc is present. */
  public boolean isValid() {
    return productId != null || (productDesc != null && !productDesc.isBlank());
  }

  /**
   * Adds confirmed shipped quantity. Uses shipmentLineId as an idempotency key to prevent
   * double-counting if the event is processed twice.
   */
  public void addShippedQuantity(UUID shipmentLineId, BigDecimal quantity) {
    if (this.processedShipmentLineIds.contains(shipmentLineId)) {
      return; // Idempotent return
    }

    if (this.shippedQty == null) {
      this.shippedQty = BigDecimal.ZERO;
    }
    this.shippedQty = this.shippedQty.add(quantity);
    this.processedShipmentLineIds.add(shipmentLineId);
  }

  @PrePersist
  @PreUpdate
  private void validateEntity() {
    if (!isValid()) {
      throw new com.fabricmanagement.sales.common.exception.OrderDomainException(
          "Either productId or productDesc must be provided for SalesOrderLine");
    }
  }
}
