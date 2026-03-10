package com.fabricmanagement.order.sales.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartner;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Type;

/**
 * Sales order entity with TradingPartner integration.
 *
 * <p>Uses trading_partner_id as the primary customer reference (Faz 1.5 pattern). This entity does
 * NOT have a legacy company_id column - it's a clean implementation.
 *
 * <h2>TradingPartner Integration:</h2>
 *
 * <ul>
 *   <li>trading_partner_id is NOT NULL - all orders must have a partner
 *   <li>Use TradingPartnerResolver in service layer to resolve partner IDs
 *   <li>For SALES orders: partner is the customer
 *   <li>For PURCHASE orders: partner is the supplier
 * </ul>
 */
@Entity
@Table(
    name = "sales_order",
    schema = "order",
    indexes = {
      @Index(name = "idx_so_tenant", columnList = "tenant_id"),
      @Index(name = "idx_so_trading_partner", columnList = "trading_partner_id"),
      @Index(name = "idx_so_status", columnList = "status"),
      @Index(name = "idx_so_order_date", columnList = "order_date")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_so_tenant_order_number",
          columnNames = {"tenant_id", "order_number"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrder extends BaseEntity {

  // ═══════════════════════════════════════════════════════════════════════════
  // TradingPartner Reference (Faz 1.5)
  // ═══════════════════════════════════════════════════════════════════════════

  /** FK to TradingPartner - primary partner reference. */
  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  /** Lazy-loaded TradingPartner relationship. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trading_partner_id", insertable = false, updatable = false)
  private TradingPartner tradingPartner;

  // ═══════════════════════════════════════════════════════════════════════════
  // Order Identification
  // ═══════════════════════════════════════════════════════════════════════════

  /** Unique order number per tenant (e.g., SO-20260202-00001). */
  @Column(name = "order_number", nullable = false, length = 50)
  private String orderNumber;

  /** Customer's purchase order reference. */
  @Column(name = "customer_reference", length = 100)
  private String customerReference;

  /** Order type. */
  @Enumerated(EnumType.STRING)
  @Column(name = "order_type", nullable = false, length = 20)
  @Builder.Default
  private OrderType orderType = OrderType.SALES;

  /** Order status. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  @Builder.Default
  private OrderStatus status = OrderStatus.DRAFT;

  // ═══════════════════════════════════════════════════════════════════════════
  // Dates
  // ═══════════════════════════════════════════════════════════════════════════

  /** Order creation date. */
  @Column(name = "order_date", nullable = false)
  private LocalDate orderDate;

  /** Customer's requested delivery date. */
  @Column(name = "requested_delivery_date")
  private LocalDate requestedDeliveryDate;

  /** Our promised delivery date. */
  @Column(name = "promised_delivery_date")
  private LocalDate promisedDeliveryDate;

  /** Actual delivery date. */
  @Column(name = "actual_delivery_date")
  private LocalDate actualDeliveryDate;

  // ═══════════════════════════════════════════════════════════════════════════
  // Financial
  // ═══════════════════════════════════════════════════════════════════════════

  /** Total order amount (before tax). */
  @Column(name = "total_amount", precision = 19, scale = 4)
  private BigDecimal totalAmount;

  /** Tax amount. */
  @Column(name = "tax_amount", precision = 19, scale = 4)
  private BigDecimal taxAmount;

  /** Discount amount. */
  @Column(name = "discount_amount", precision = 19, scale = 4)
  private BigDecimal discountAmount;

  /** Currency code (ISO 4217). */
  @Column(name = "currency", length = 3)
  @Builder.Default
  private String currency = "TRY";

  // ═══════════════════════════════════════════════════════════════════════════
  // Shipping
  // ═══════════════════════════════════════════════════════════════════════════

  /** Shipping address. */
  @Column(name = "shipping_address", length = 500)
  private String shippingAddress;

  /** Billing address. */
  @Column(name = "billing_address", length = 500)
  private String billingAddress;

  /** Shipping method. */
  @Column(name = "shipping_method", length = 50)
  private String shippingMethod;

  // ═══════════════════════════════════════════════════════════════════════════
  // Metadata
  // ═══════════════════════════════════════════════════════════════════════════

  /** Notes/comments. */
  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  /** Flexible metadata (payment terms, incoterms, etc.). */
  @Type(JsonType.class)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  // ═══════════════════════════════════════════════════════════════════════════
  // BaseEntity
  // ═══════════════════════════════════════════════════════════════════════════

  @Override
  protected String getModuleCode() {
    return "SO";
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Business Methods
  // ═══════════════════════════════════════════════════════════════════════════

  /** Confirm the order (DRAFT → CONFIRMED). */
  public void confirm() {
    if (status != OrderStatus.DRAFT) {
      throw new IllegalStateException("Can only confirm orders in DRAFT status");
    }
    this.status = OrderStatus.CONFIRMED;
  }

  /** Start processing (CONFIRMED → IN_PROGRESS). */
  public void startProcessing() {
    if (status != OrderStatus.CONFIRMED) {
      throw new IllegalStateException("Can only start processing CONFIRMED orders");
    }
    this.status = OrderStatus.IN_PROGRESS;
  }

  /** Mark as shipped. */
  public void ship() {
    if (!status.canShip()) {
      throw new IllegalStateException("Cannot ship order in status: " + status);
    }
    this.status = OrderStatus.SHIPPED;
  }

  /** Mark as delivered. */
  public void deliver(LocalDate deliveryDate) {
    if (status != OrderStatus.SHIPPED) {
      throw new IllegalStateException("Can only deliver SHIPPED orders");
    }
    this.status = OrderStatus.DELIVERED;
    this.actualDeliveryDate = deliveryDate;
  }

  /** Cancel the order. */
  public void cancel() {
    if (!status.canCancel()) {
      throw new IllegalStateException("Cannot cancel order in status: " + status);
    }
    this.status = OrderStatus.CANCELLED;
  }

  /** Put order on hold. */
  public void hold() {
    if (status.isTerminal()) {
      throw new IllegalStateException("Cannot hold order in terminal status: " + status);
    }
    this.status = OrderStatus.ON_HOLD;
  }

  /** Calculate grand total. */
  public BigDecimal getGrandTotal() {
    BigDecimal base = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    BigDecimal tax = taxAmount != null ? taxAmount : BigDecimal.ZERO;
    BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
    return base.add(tax).subtract(discount);
  }
}
