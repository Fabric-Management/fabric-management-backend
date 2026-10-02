package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
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
 * <p>Each line is one distribution of a catalogue product (SOI K04): the product, colour, finished
 * width, unit, quantity, price and optional delivery date. {@code productId} is mandatory (SOI
 * K02); {@code productDesc} is an optional line note and never a substitute for the product.
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
public class SalesOrderLine extends BaseEntity implements CatalogLineInput {

  // ── References ───────────────────────────────────────────────────────────

  /** FK → SalesOrder. */
  @Column(name = "sales_order_id", nullable = false)
  private UUID salesOrderId;

  /** FK → Product. Mandatory: a catalogue line always names a product (SOI K02). */
  @Column(name = "product_id", nullable = false)
  private UUID productId;

  /** Optional line note. Never a substitute for {@link #productId}. */
  @Column(name = "product_desc", columnDefinition = "TEXT")
  private String productDesc;

  // ── Distribution (SOI K04/K05) ───────────────────────────────────────────

  /** Colour card of this distribution; must be an active tenant colour card when present. */
  @Column(name = "color_id")
  private UUID colorId;

  /** Finished (not greige) width; one of the product's defined widths (SOI K11, R05). */
  @Column(name = "finished_width", precision = 8, scale = 2)
  private BigDecimal finishedWidth;

  @Column(name = "finished_width_unit", length = 10)
  private String finishedWidthUnit;

  /** Customer-requested delivery date of this distribution, when it differs per distribution. */
  @Column(name = "requested_delivery_date")
  private java.time.LocalDate requestedDeliveryDate;

  /** The customer requires the whole distribution from a single dye lot (SOI R11). */
  @Column(name = "single_lot_required", nullable = false)
  @Builder.Default
  private boolean singleLotRequired = false;

  // ── Quantities & Pricing ─────────────────────────────────────────────────

  @Column(name = "requested_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal requestedQty;

  /**
   * Quantity first requested by the customer. Immutable: proposals and accepted quantities are
   * recorded separately and never overwrite it (SOI K08).
   */
  @Column(
      name = "initial_requested_qty",
      nullable = false,
      updatable = false,
      precision = 15,
      scale = 3)
  @Setter(AccessLevel.NONE)
  private BigDecimal initialRequestedQty;

  @Column(name = "shipped_qty", nullable = false, precision = 15, scale = 3)
  @Builder.Default
  private BigDecimal shippedQty = BigDecimal.ZERO;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  /**
   * The agreed sales currency of this line (ISO 4217). Lines of one order may use different
   * currencies; it can be known before the price is agreed.
   */
  @Column(name = "currency", length = 3)
  @Setter(AccessLevel.NONE)
  private String currency;

  /** Agreed unit price exactly as stored (4 decimals). Amount arithmetic uses this, not Money. */
  @Column(name = "unit_price", precision = 18, scale = 4)
  @Setter(AccessLevel.NONE)
  private BigDecimal unitPriceAmount;

  /** Discount on this line, in the line currency. Requires an agreed unit price. */
  @Column(name = "discount_amount", precision = 18, scale = 4)
  @Setter(AccessLevel.NONE)
  private BigDecimal discountAmountValue;

  /** Tax on this line, in the line currency. Requires an agreed unit price. */
  @Column(name = "tax_amount", precision = 18, scale = 4)
  @Setter(AccessLevel.NONE)
  private BigDecimal taxAmountValue;

  /**
   * Agreed unit price as {@link Money}, or {@code null} while not agreed. {@link Money} rounds to
   * the currency's minor unit, so it is for display and comparison only: totals and validation use
   * {@link #getUnitPriceAmount()}.
   */
  public Money getUnitPrice() {
    return unitPriceAmount == null ? null : Money.of(unitPriceAmount, currency);
  }

  public Money getDiscountAmount() {
    return discountAmountValue == null ? null : Money.of(discountAmountValue, currency);
  }

  public Money getTaxAmount() {
    return taxAmountValue == null ? null : Money.of(taxAmountValue, currency);
  }

  /** Keeps the agreed currency and adjustments; a cleared price also clears the adjustments. */
  public void updateUnitPrice(Money price) {
    updatePricing(
        price != null ? price.getCurrency().getCurrencyCode() : currency,
        price != null ? price.getAmount() : null,
        price != null ? discountAmountValue : null,
        price != null ? taxAmountValue : null);
  }

  /** Replaces the commercial terms of this line after checking them against its quantity. */
  public void updatePricing(
      String newCurrency, BigDecimal unitPrice, BigDecimal discount, BigDecimal tax) {
    validatePricing(requestedQty, newCurrency, unitPrice, discount, tax);
    this.currency = newCurrency;
    this.unitPriceAmount = unitPrice;
    this.discountAmountValue = discount;
    this.taxAmountValue = tax;
  }

  /** A changed quantity must still carry the recorded discount (SOI R08). */
  public void assertAdjustmentsFitQuantity() {
    validatePricing(requestedQty, currency, unitPriceAmount, discountAmountValue, taxAmountValue);
  }

  /**
   * Commercial rules of a line: a price needs a currency; discount and tax need a price, cannot be
   * negative, and the discount cannot exceed the line amount.
   */
  public static void validatePricing(
      BigDecimal quantity,
      String currency,
      BigDecimal unitPrice,
      BigDecimal discount,
      BigDecimal tax) {
    if (unitPrice != null && (currency == null || currency.isBlank())) {
      throw new OrderDomainException("A priced line must name its currency");
    }
    if (unitPrice != null && unitPrice.signum() < 0) {
      throw new OrderDomainException("Unit price cannot be negative");
    }
    if ((discount != null || tax != null) && unitPrice == null) {
      throw new OrderDomainException("Discount and tax need an agreed unit price");
    }
    if ((discount != null && discount.signum() < 0) || (tax != null && tax.signum() < 0)) {
      throw new OrderDomainException("Discount and tax cannot be negative");
    }
    if (discount != null
        && quantity != null
        && discount.compareTo(unitPrice.multiply(quantity)) > 0) {
      throw new OrderDomainException("Line discount cannot exceed the line amount");
    }
  }

  // ── Agreed quantity tolerance (SOI A03), per distribution ────────────────

  /** How far above the requested quantity the customer accepts, in percent. */
  @Column(name = "tolerance_up_pct", precision = 5, scale = 2)
  @Setter(AccessLevel.NONE)
  private BigDecimal toleranceUpPct;

  /** How far below the requested quantity the customer accepts, in percent. */
  @Column(name = "tolerance_down_pct", precision = 5, scale = 2)
  @Setter(AccessLevel.NONE)
  private BigDecimal toleranceDownPct;

  @Column(name = "tolerance_recorded_by")
  @Setter(AccessLevel.NONE)
  private UUID toleranceRecordedBy;

  @Column(name = "tolerance_recorded_at")
  @Setter(AccessLevel.NONE)
  private java.time.Instant toleranceRecordedAt;

  /**
   * Records the quantity tolerance agreed for this distribution, or clears it when both limits are
   * null. The recorder and time are kept with it; where it was agreed is the order's agreement
   * source. Re-recording the same limits keeps the original recorder and time, so an unrelated edit
   * does not rewrite provenance.
   */
  public void recordTolerance(
      BigDecimal upPct, BigDecimal downPct, UUID actor, java.time.Instant at) {
    if (upPct == null && downPct == null) {
      toleranceUpPct = null;
      toleranceDownPct = null;
      toleranceRecordedBy = null;
      toleranceRecordedAt = null;
      return;
    }
    validateTolerance(upPct, downPct);
    if (sameAmount(upPct, toleranceUpPct) && sameAmount(downPct, toleranceDownPct)) {
      return;
    }
    if (actor == null || at == null) {
      throw new OrderDomainException("An agreed tolerance needs its recorder and time");
    }
    toleranceUpPct = upPct;
    toleranceDownPct = downPct;
    toleranceRecordedBy = actor;
    toleranceRecordedAt = at;
  }

  /** Limits are 0–100%. */
  public static void validateTolerance(BigDecimal upPct, BigDecimal downPct) {
    for (BigDecimal pct : new BigDecimal[] {upPct, downPct}) {
      if (pct != null && (pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0)) {
        throw new OrderDomainException("An agreed tolerance is between 0 and 100 percent");
      }
    }
  }

  private static boolean sameAmount(BigDecimal left, BigDecimal right) {
    return left == null ? right == null : right != null && left.compareTo(right) == 0;
  }

  /** Lombok fills the rest of the builder; a {@link Money} price sets amount and currency. */
  public static class SalesOrderLineBuilder {
    public SalesOrderLineBuilder unitPrice(Money price) {
      this.unitPriceAmount = price == null ? null : price.getAmount();
      if (price != null) {
        this.currency = price.getCurrency().getCurrencyCode();
      }
      return this;
    }
  }

  public void attachRequirementProfile(RequirementProfileSnapshot snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Requirement profile snapshot is required");
    }
    if (requirementProfileId != null && !requirementProfileId.equals(snapshot.profileId())) {
      throw new com.fabricmanagement.sales.common.exception.OrderDomainException(
          "A sales-order line cannot change requirement profile identity");
    }
    if (requirementProfileVersion != null
        && snapshot.profileVersion() <= requirementProfileVersion) {
      throw new com.fabricmanagement.sales.common.exception.OrderDomainException(
          "Requirement profile version must increase");
    }
    requirementProfileId = snapshot.profileId();
    requirementProfileVersion = snapshot.profileVersion();
    requirementProfileFingerprint = snapshot.fingerprint();
    requirementProfileSnapshot = snapshot;
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

  // ── Typed requirement profile ─────────────────────────────────────────────

  @Column(name = "requirement_profile_id")
  @Setter(AccessLevel.NONE)
  private UUID requirementProfileId;

  @Column(name = "requirement_profile_version")
  @Setter(AccessLevel.NONE)
  private Integer requirementProfileVersion;

  @Column(name = "requirement_profile_fingerprint", length = 64)
  @Setter(AccessLevel.NONE)
  private String requirementProfileFingerprint;

  /** Current snapshot; historical versions are retained in the append-only profile table. */
  @Type(JsonType.class)
  @Column(name = "requirement_profile_snapshot", columnDefinition = "jsonb")
  @Setter(AccessLevel.NONE)
  private RequirementProfileSnapshot requirementProfileSnapshot;

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

  /**
   * Moves the line forward when linked production starts.
   *
   * @return true when the status changed, false when the event was duplicate or out of order
   */
  public boolean markInProduction() {
    if (this.lineStatus != SalesOrderLineStatus.RECIPE_ASSIGNED) {
      return false;
    }
    this.lineStatus = SalesOrderLineStatus.IN_PRODUCTION;
    return true;
  }

  /**
   * Moves the line forward when linked production is completed.
   *
   * @return true when the status changed, false when the event was duplicate or out of order
   */
  public boolean markCompleted() {
    if (this.lineStatus != SalesOrderLineStatus.IN_PRODUCTION) {
      return false;
    }
    this.lineStatus = SalesOrderLineStatus.COMPLETED;
    return true;
  }

  /**
   * Moves the line forward when production output is fully stored in warehouse. Idempotent: returns
   * false if already IN_WAREHOUSE or beyond (no exception thrown).
   *
   * @return true when the status changed, false when the event was duplicate or out of order
   */
  public boolean markInWarehouse() {
    if (this.lineStatus != SalesOrderLineStatus.COMPLETED) {
      return false;
    }
    this.lineStatus = SalesOrderLineStatus.IN_WAREHOUSE;
    return true;
  }

  /**
   * Replaces the product after a traced correction (SOI R19). The requirement profile was resolved
   * for the old product, so it is dropped and must be resolved again from a new basis.
   */
  public void correctProduct(UUID newProductId) {
    if (newProductId == null || newProductId.equals(productId)) {
      throw new com.fabricmanagement.sales.common.exception.OrderDomainException(
          "A product correction names a different product");
    }
    this.productId = newProductId;
    this.requirementProfileId = null;
    this.requirementProfileVersion = null;
    this.requirementProfileFingerprint = null;
    this.requirementProfileSnapshot = null;
  }

  /** A line is valid only when it names a product (SOI K02). */
  public boolean isValid() {
    return productId != null;
  }

  /** Records the first requested quantity once; later quantity changes never overwrite it. */
  public void captureInitialRequestIfAbsent() {
    if (initialRequestedQty == null) {
      initialRequestedQty = requestedQty;
    }
  }

  /** True when the line carries a finished-width requirement. */
  public boolean hasFinishedWidth() {
    return finishedWidth != null;
  }

  /**
   * Adds confirmed shipped quantity. Uses shipmentLineId as an idempotency key to prevent
   * double-counting if the event is processed twice.
   *
   * @return true when the shipment quantity was applied, false when this shipment line was already
   *     processed
   */
  public boolean addShippedQuantity(UUID shipmentLineId, BigDecimal quantity) {
    if (this.processedShipmentLineIds.contains(shipmentLineId)) {
      return false; // Idempotent return
    }

    if (this.shippedQty == null) {
      this.shippedQty = BigDecimal.ZERO;
    }
    this.shippedQty = this.shippedQty.add(quantity);
    this.processedShipmentLineIds.add(shipmentLineId);
    return true;
  }

  public BigDecimal getRemainingQty() {
    BigDecimal requested = requestedQty != null ? requestedQty : BigDecimal.ZERO;
    BigDecimal shipped = shippedQty != null ? shippedQty : BigDecimal.ZERO;
    return requested.subtract(shipped);
  }

  public boolean isOverShipped() {
    return requestedQty != null && shippedQty != null && shippedQty.compareTo(requestedQty) > 0;
  }

  @PrePersist
  @PreUpdate
  private void validateEntity() {
    captureInitialRequestIfAbsent();
    if (!isValid()) {
      throw new com.fabricmanagement.sales.common.exception.OrderDomainException(
          "A sales-order line must name a product");
    }
    if ((finishedWidth == null) != (finishedWidthUnit == null)) {
      throw new com.fabricmanagement.sales.common.exception.OrderDomainException(
          "Finished width and its unit must be given together");
    }
    if (Boolean.TRUE.equals(getIsActive())
        && (requestedQty == null || requestedQty.compareTo(BigDecimal.ZERO) <= 0)) {
      throw new com.fabricmanagement.sales.common.exception.OrderDomainException(
          "Requested quantity must be greater than zero for SalesOrderLine");
    }
  }
}
