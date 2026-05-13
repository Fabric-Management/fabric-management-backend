package com.fabricmanagement.procurement.purchaseorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
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
@Builder
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

  @Column(name = "qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal qty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  /** Computed: qty × unitPrice. Stored for reporting performance. */
  @Column(name = "total_price", nullable = false, precision = 18, scale = 3)
  private BigDecimal totalPrice;

  /** Line-level module-specific data. Stored as JSONB. */
  @org.hibernate.annotations.Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
  @Column(name = "module_specs", columnDefinition = "jsonb")
  @Builder.Default
  private com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs
      moduleSpecs =
          new com.fabricmanagement.procurement.purchaseorder.domain.specs.GenericPurchaseSpecs(
              null);

  @Override
  protected String getModuleCode() {
    return "POL";
  }
}
