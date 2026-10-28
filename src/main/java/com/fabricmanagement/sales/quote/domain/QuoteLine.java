package com.fabricmanagement.sales.quote.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "quote_line", schema = "sales")
@Getter
@Setter
@NoArgsConstructor
public class QuoteLine extends BaseEntity {

  // No explicit quoteId field, mapped by @JoinColumn in Quote

  @Column(name = "product_id")
  private UUID productId;

  @Column(name = "product_desc", columnDefinition = "TEXT")
  private String productDesc;

  @Column(name = "quality_grade_id")
  private UUID qualityGradeId;

  @Column(name = "quality_grade_code", length = 10)
  private String qualityGradeCode;

  @Column(name = "quality_grade_name", length = 255)
  private String qualityGradeName;

  @Column(name = "quality_price_factor", precision = 4, scale = 3)
  private BigDecimal qualityPriceFactor;

  @Column(name = "color_id")
  private UUID colorId;

  @Column(name = "color_code", length = 50)
  private String colorCode;

  @Column(name = "color_name", length = 255)
  private String colorName;

  @Column(name = "color_hex", length = 7)
  private String colorHex;

  @Type(JsonType.class)
  @Column(name = "lot_snapshot", columnDefinition = "jsonb", nullable = false)
  private String lotSnapshot = "[]";

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_status", length = 30)
  private QuoteLineDeliveryStatus deliveryStatus;

  @Column(name = "delivery_date")
  private LocalDate deliveryDate;

  @Column(name = "delivery_covered")
  private Boolean deliveryCovered;

  @Enumerated(EnumType.STRING)
  @Column(name = "fulfillment_mode", length = 32)
  private FulfillmentMode fulfillmentMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "fulfillment_determination_status", nullable = false, length = 16)
  private FulfillmentDeterminationStatus fulfillmentDeterminationStatus =
      FulfillmentDeterminationStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "fulfillment_determination_method", length = 16)
  private FulfillmentDeterminationMethod fulfillmentDeterminationMethod;

  @Column(name = "requested_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal requestedQty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "list_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal listPrice;

  @Column(name = "offered_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal offeredPrice;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "discount_rate", nullable = false, precision = 5, scale = 4)
  private BigDecimal discountRate;

  @Column(name = "profit_margin", precision = 5, scale = 4)
  private BigDecimal profitMargin;

  @Enumerated(EnumType.STRING)
  @Column(name = "price_zone", nullable = false, length = 30)
  private QuotePriceZone priceZone;

  @Type(JsonType.class)
  @Column(name = "module_specs", columnDefinition = "jsonb", nullable = false)
  private String moduleSpecs = "{}";

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public BigDecimal lineTotal() {
    if (offeredPrice == null || requestedQty == null) {
      return BigDecimal.ZERO;
    }
    return offeredPrice.multiply(requestedQty);
  }

  @Column(name = "finished_width", precision = 8, scale = 2)
  private BigDecimal finishedWidth;

  @Column(name = "finished_width_unit", length = 10)
  private String finishedWidthUnit;

  @Column(name = "single_lot_required", nullable = false)
  private boolean singleLotRequired;

  @Column(name = "tolerance_up_pct", precision = 5, scale = 2)
  private BigDecimal toleranceUpPct;

  @Column(name = "tolerance_down_pct", precision = 5, scale = 2)
  private BigDecimal toleranceDownPct;

  @Column(name = "customer_request_qty", precision = 15, scale = 3)
  private BigDecimal customerRequestQty;

  @Column(name = "initial_requested_qty", precision = 15, scale = 3, updatable = false)
  private BigDecimal initialRequestedQty;

  @jakarta.persistence.Embedded
  private com.fabricmanagement.sales.common.domain.SalesQuantityTerms quantityTerms =
      new com.fabricmanagement.sales.common.domain.SalesQuantityTerms();

  public com.fabricmanagement.sales.common.domain.SalesQuantityPolicy getQuantityPolicy() {
    return quantityTerms == null
        ? com.fabricmanagement.sales.common.domain.SalesQuantityPolicy.UNRECORDED
        : quantityTerms.getPolicy();
  }

  public void captureCustomerRequest(BigDecimal quantity) {
    customerRequestQty = quantity;
    if (initialRequestedQty == null) initialRequestedQty = quantity;
  }

  public void applyStockQuantity(BigDecimal original, BigDecimal selected) {
    getQuantityPolicy().assertQuantity(original, selected);
    customerRequestQty = original;
    requestedQty = selected;
  }

  public void recordQuantityPolicy(
      com.fabricmanagement.sales.common.domain.SalesQuantityPolicy policy,
      UUID actor,
      java.time.Instant at,
      String reason) {
    if (quantityTerms == null)
      quantityTerms = new com.fabricmanagement.sales.common.domain.SalesQuantityTerms();
    if (policy != null) policy.assertQuantity(customerRequestQty, requestedQty);
    quantityTerms.record(policy, actor, at, reason);
  }

  public void updateEditableFields(BigDecimal requestedQty, String unit, BigDecimal offeredPrice) {
    if (this.requestedQty != null && this.requestedQty.compareTo(requestedQty) != 0)
      captureCustomerRequest(requestedQty);
    this.requestedQty = requestedQty;
    this.unit = unit;
    this.offeredPrice = offeredPrice;
  }

  @jakarta.persistence.PrePersist
  @jakarta.persistence.PreUpdate
  void validateQuantityTerms() {
    getQuantityPolicy().assertQuantity(customerRequestQty, requestedQty);
  }

  public void applyQualityGrade(
      UUID qualityGradeId,
      String qualityGradeCode,
      String qualityGradeName,
      BigDecimal qualityPriceFactor) {
    this.qualityGradeId = qualityGradeId;
    this.qualityGradeCode = qualityGradeCode;
    this.qualityGradeName = qualityGradeName;
    this.qualityPriceFactor = qualityPriceFactor;
  }

  public void clearQualityGrade() {
    applyQualityGrade(null, null, null, null);
  }

  public void applyColor(UUID colorId, String colorCode, String colorName, String colorHex) {
    this.colorId = colorId;
    this.colorCode = colorCode;
    this.colorName = colorName;
    this.colorHex = colorHex;
  }

  public void clearColor() {
    applyColor(null, null, null, null);
  }

  public void applyLotSnapshot(String lotSnapshot) {
    this.lotSnapshot = lotSnapshot == null || lotSnapshot.isBlank() ? "[]" : lotSnapshot;
  }

  public void applyDelivery(
      QuoteLineDeliveryStatus deliveryStatus, LocalDate deliveryDate, Boolean deliveryCovered) {
    this.deliveryStatus = deliveryStatus;
    this.deliveryDate = deliveryDate;
    this.deliveryCovered = deliveryCovered;
  }

  public void applyManualFulfillmentMode(FulfillmentMode fulfillmentMode) {
    this.fulfillmentMode = fulfillmentMode;
    this.fulfillmentDeterminationStatus =
        fulfillmentMode == null
            ? FulfillmentDeterminationStatus.PENDING
            : FulfillmentDeterminationStatus.CONFIRMED;
    this.fulfillmentDeterminationMethod =
        fulfillmentMode == null ? null : FulfillmentDeterminationMethod.MANUAL;
  }

  public void copyFulfillmentDeterminationFrom(QuoteLine source) {
    this.fulfillmentMode = source.getFulfillmentMode();
    this.fulfillmentDeterminationStatus = source.getFulfillmentDeterminationStatus();
    this.fulfillmentDeterminationMethod = source.getFulfillmentDeterminationMethod();
  }

  public void applyPricing(
      BigDecimal discountRate, BigDecimal profitMargin, QuotePriceZone priceZone) {
    this.discountRate = discountRate;
    this.profitMargin = profitMargin;
    this.priceZone = priceZone;
  }

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "SALES";
  }
}
