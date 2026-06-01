package com.fabricmanagement.procurement.purchaseorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.web.exception.CurrencyMismatchException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.GenericPurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A confirmed purchase order issued to a supplier.
 *
 * <p>Table: {@code procurement.purchase_order}
 *
 * <p>Created either:
 *
 * <ul>
 *   <li>via RFQ → SupplierQuote flow (supplierQuoteId set)
 *   <li>via direct creation for contracted suppliers (supplierQuoteId null)
 * </ul>
 */
@Entity
@Table(name = "purchase_order", schema = "procurement")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PurchaseOrder extends BaseEntity {

  /**
   * Auto-generated business key. Pre-PR-2 records use legacy PO-{YEAR}-{8-HEX} format. New records
   * use PO-{YYYYMMDD}-{NNNNN}. Both coexist; searchText substring filter is format-agnostic.
   */
  @Column(name = "po_number", nullable = false, length = 50)
  private String poNumber;

  /** FK → WorkOrder. The production order that triggered this purchase. */
  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  /** FK → TradingPartner (supplier). */
  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  /** FK → SupplierQuote — populated when PO was created from an RFQ flow. */
  @Column(name = "supplier_quote_id")
  private UUID supplierQuoteId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 25)
  private PurchaseOrderStatus status;

  @Column(name = "payment_terms", length = 20)
  private String paymentTerms;

  @Column(name = "expected_delivery")
  private LocalDate expectedDelivery;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "total_amount", nullable = false, precision = 18, scale = 3)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", nullable = false, length = 3))
  })
  @Setter(AccessLevel.NONE)
  private Money totalAmount;

  public void updateTotalAmount(Money amount) {
    if (amount == null) {
      throw new IllegalArgumentException("Total amount cannot be null");
    }
    if (this.totalAmount != null && !this.totalAmount.getCurrency().equals(amount.getCurrency())) {
      // Allow initial set or same currency update
      throw new CurrencyMismatchException(
          this.totalAmount.getCurrency().getCurrencyCode(), amount.getCurrency().getCurrencyCode());
    }
    this.totalAmount = amount;
  }

  public String getCurrency() {
    if (totalAmount == null) {
      throw new IllegalStateException("PurchaseOrder totalAmount cannot be null");
    }
    return totalAmount.getCurrency().getCurrencyCode();
  }

  /** Starts at 1, incremented on each amendment. */
  @Column(name = "revision_number", nullable = false)
  @Builder.Default
  private Integer revisionNumber = 1;

  @Column(name = "change_reason", columnDefinition = "TEXT")
  private String changeReason;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @org.hibernate.annotations.Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
  @Column(name = "attachments", columnDefinition = "jsonb")
  @Builder.Default
  private List<Map<String, Object>> attachments = List.of();

  @Enumerated(EnumType.STRING)
  @Column(name = "module_type", nullable = false, length = 30)
  @Builder.Default
  private PurchaseOrderModuleType moduleType = PurchaseOrderModuleType.GENERIC;

  /** Module-specific specs — Value Object, stored as JSONB. */
  @org.hibernate.annotations.Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
  @Column(name = "module_specs", columnDefinition = "jsonb")
  @Builder.Default
  private PurchaseOrderSpecs moduleSpecs = new GenericPurchaseSpecs(null);

  @Override
  protected String getModuleCode() {
    return "PO";
  }
}
