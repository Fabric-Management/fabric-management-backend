package com.fabricmanagement.procurement.purchaseorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single product line within a PurchaseOrder.
 *
 * <p>Table: {@code procurement.purchase_order_line}
 */
@Entity
@Table(name = "purchase_order_line", schema = "procurement")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PurchaseOrderLine extends BaseEntity {

  @Column(name = "purchase_order_id", nullable = false)
  private UUID purchaseOrderId;

  /** FK → SupplierRFQLine — present when originating from RFQ. */
  @Column(name = "rfq_line_id")
  private UUID rfqLineId;

  /** FK → Product — may be null if using free-text productDesc. */
  @Column(name = "product_id")
  private UUID productId;

  /** Free-text product description (used when no Product entity exists yet). */
  @Column(name = "product_desc", columnDefinition = "TEXT")
  private String productDesc;

  @Setter(AccessLevel.NONE)
  @Column(name = "qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal qty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Setter(AccessLevel.NONE)
  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", nullable = false, length = 3))
  })
  private Money unitPrice;

  /** Computed: qty × unitPrice. Stored for reporting performance. Never set externally. */
  @Setter(AccessLevel.NONE)
  @Column(name = "total_price", nullable = false, precision = 18, scale = 3)
  private BigDecimal totalPrice;

  /** Line-level module-specific data. Stored as JSONB. */
  @org.hibernate.annotations.Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
  @Column(name = "module_specs", columnDefinition = "jsonb")
  private com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs
      moduleSpecs =
          new com.fabricmanagement.procurement.purchaseorder.domain.specs.GenericPurchaseSpecs(
              null);

  // ── Factory method — preferred over builder for domain code ──────────────────

  /** Creates a fully-initialised PurchaseOrderLine with totalPrice already computed. */
  @Builder
  public static PurchaseOrderLine create(
      UUID purchaseOrderId,
      UUID rfqLineId,
      UUID productId,
      String productDesc,
      BigDecimal qty,
      String unit,
      Money unitPrice,
      com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs moduleSpecs) {
    PurchaseOrderLine line = new PurchaseOrderLine();
    line.purchaseOrderId = purchaseOrderId;
    line.rfqLineId = rfqLineId;
    line.productId = productId;
    line.productDesc = productDesc;
    line.qty = qty;
    line.unit = unit;
    line.unitPrice = unitPrice;
    line.moduleSpecs =
        moduleSpecs != null
            ? moduleSpecs
            : new com.fabricmanagement.procurement.purchaseorder.domain.specs.GenericPurchaseSpecs(
                null);
    line.recalculateTotal();
    return line;
  }

  @Override
  protected String getModuleCode() {
    return "POL";
  }

  // ── Domain setters with auto-recalculation ──────────────────────────────────

  public void setQty(BigDecimal qty) {
    this.qty = qty;
    recalculateTotal();
  }

  public void setUnitPrice(Money price) {
    this.unitPrice = price;
    recalculateTotal();
  }

  public Money getTotalAsMoney() {
    if (this.totalPrice == null || this.unitPrice == null) {
      return null;
    }
    return Money.of(this.totalPrice, this.unitPrice.getCurrency().getCurrencyCode());
  }

  private void recalculateTotal() {
    if (this.qty != null && this.unitPrice != null) {
      this.totalPrice =
          this.qty.multiply(this.unitPrice.getAmount()).setScale(3, RoundingMode.HALF_UP);
    }
  }

  // ── JPA lifecycle validation ────────────────────────────────────────────────

  @PrePersist
  @PreUpdate
  private void validateEntity() {
    if (this.productId == null && (this.productDesc == null || this.productDesc.isBlank())) {
      throw new ProcurementDomainException(
          "Either productId or productDesc must be provided for PurchaseOrderLine");
    }
    recalculateTotal();
  }
}
