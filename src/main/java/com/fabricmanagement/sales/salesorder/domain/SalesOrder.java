package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.offline.domain.OfflineMetadata;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
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
    schema = "sales_ord",
    indexes = {
      @Index(name = "idx_so_tenant", columnList = "tenant_id"),
      @Index(name = "idx_so_trading_partner", columnList = "trading_partner_id"),
      @Index(name = "idx_so_status", columnList = "status"),
      @Index(name = "idx_so_order_date", columnList = "order_date")
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

  @Setter(AccessLevel.NONE)
  @Embedded
  @Builder.Default
  private OrderTotals totals = OrderTotals.zero("TRY");

  public void updateTotals(OrderTotals newTotals) {
    if (newTotals == null) {
      throw new IllegalArgumentException("OrderTotals cannot be null");
    }
    this.totals = newTotals;
  }

  /** Helper to get currency code */
  public String getCurrency() {
    if (totals == null) {
      throw new IllegalStateException("SalesOrder totals cannot be null");
    }
    return totals.getCurrency();
  }

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
  // Faz 2 — Module & Traceability
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Production module type for this order (FIBER / YARN / FABRIC / DYE_FINISHING). Drives
   * moduleSpecs validation on SalesOrderLine level.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "module_type", length = 20)
  private ModuleType moduleType;

  /** Customer-requested deadline for delivery of all lines. */
  @Column(name = "deadline")
  private LocalDate deadline;

  /** FK → Quote — populated when order was converted from a quote. */
  @Column(name = "quote_id")
  private UUID quoteId;

  /** FK → SampleRequest — populated when order originated from a sample request. */
  @Column(name = "sample_request_id")
  private UUID sampleRequestId;

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
  // Offline Sync
  // ═══════════════════════════════════════════════════════════════════════════
  @Embedded private OfflineMetadata offlineMetadata;

  // ═══════════════════════════════════════════════════════════════════════════
  // BaseEntity
  // ═══════════════════════════════════════════════════════════════════════════

  @Override
  protected String getModuleCode() {
    return "SO";
  }

  @Override
  public void delete() {
    if (!status.canDelete()) {
      throw new OrderDomainException(
          "Only DRAFT orders can be deleted. Current status: "
              + status
              + ". Use cancel() for non-draft orders.");
    }
    super.delete();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Business Methods
  // ═══════════════════════════════════════════════════════════════════════════

  /** Mark order as pending approval. */
  public void pendingApproval() {
    if (status != OrderStatus.DRAFT) {
      throw new OrderDomainException(
          "Only DRAFT orders can be sent for approval. Current: " + status);
    }
    this.status = OrderStatus.PENDING_APPROVAL;
  }

  /** Reject the order during approval. */
  public void reject(String reason) {
    if (status != OrderStatus.PENDING_APPROVAL) {
      throw new OrderDomainException(
          "Only PENDING_APPROVAL orders can be rejected. Current: " + status);
    }
    this.status = OrderStatus.REJECTED;
  }

  /**
   * Update order details. Only allowed in DRAFT status. Rich Domain Model — edit guard lives in the
   * entity.
   *
   * @throws OrderDomainException with HTTP 409 if order is not in DRAFT status
   */
  public void updateDraft(SalesOrderUpdateCommand cmd) {
    if (!status.canEdit()) {
      throw new OrderDomainException(
          "Cannot edit order "
              + orderNumber
              + ": current status "
              + status
              + " does not allow editing. Only DRAFT orders can be modified.",
          409);
    }
    this.customerReference = cmd.customerReference();
    this.orderDate = cmd.orderDate();
    this.requestedDeliveryDate = cmd.requestedDeliveryDate();
    this.promisedDeliveryDate = cmd.promisedDeliveryDate();
    this.updateTotals(cmd.totals());
    this.shippingAddress = cmd.shippingAddress();
    this.billingAddress = cmd.billingAddress();
    this.shippingMethod = cmd.shippingMethod();
    this.notes = cmd.notes();
    this.metadata = cmd.metadata();
    this.moduleType = cmd.moduleType();
    this.deadline = cmd.deadline();
  }

  /** Confirm the order (DRAFT → CONFIRMED). */
  public void confirm() {
    if (status != OrderStatus.DRAFT) {
      if (status == OrderStatus.PENDING_APPROVAL) {
        throw new OrderDomainException(
            "Order is awaiting approval; cannot be confirmed manually", 409);
      }
      throw new OrderDomainException(
          String.format("Order can only be confirmed from DRAFT status. Current: %s", status), 409);
    }
    this.status = OrderStatus.CONFIRMED;
  }

  /** Confirm the order from approval workflow (PENDING_APPROVAL → CONFIRMED). */
  public void confirmFromApproval() {
    if (status != OrderStatus.PENDING_APPROVAL) {
      throw new OrderDomainException(
          String.format(
              "Order can only be confirmed from PENDING_APPROVAL status in this workflow. Current: %s",
              status),
          409);
    }
    this.status = OrderStatus.CONFIRMED;
  }

  /** Start processing (CONFIRMED → IN_PRODUCTION). */
  public void startProcessing() {
    if (status != OrderStatus.CONFIRMED) {
      throw new OrderDomainException(
          String.format(
              "Cannot start processing order %s: current status is %s (must be CONFIRMED)",
              orderNumber, status));
    }
    this.status = OrderStatus.IN_PROGRESS;
  }

  /** Mark as shipped. */
  public void ship() {
    if (!status.canShip()) {
      throw new OrderDomainException(
          String.format(
              "Cannot ship order %s: current status %s does not allow shipping",
              orderNumber, status));
    }
    this.status = OrderStatus.SHIPPED;
  }

  /** Mark as delivered. */
  public void deliver(LocalDate deliveryDate) {
    if (status != OrderStatus.SHIPPED) {
      throw new OrderDomainException(
          String.format(
              "Cannot deliver order %s: current status is %s (must be SHIPPED)",
              orderNumber, status));
    }
    this.status = OrderStatus.DELIVERED;
    this.actualDeliveryDate = deliveryDate;
  }

  /** Cancel the order. */
  public void cancel() {
    if (!status.canCancel()) {
      throw new OrderDomainException(
          String.format(
              "Cannot cancel order %s: current status %s does not allow cancellation",
              orderNumber, status));
    }
    this.status = OrderStatus.CANCELLED;
  }

  /** Put order on hold. */
  public void hold() {
    if (status.isTerminal()) {
      throw new OrderDomainException(
          String.format("Cannot hold order %s: status %s is terminal", orderNumber, status));
    }
    this.status = OrderStatus.ON_HOLD;
  }

  /**
   * Sevkiyat ilerlemesine göre header status'ünü günceller. Listener, tüm aktif satırların sevk
   * durumunu aggregate edip bu metodu çağırır.
   *
   * <p>Geçiş kuralları:
   *
   * <ul>
   *   <li>Terminal durumlarda (DELIVERED/CANCELLED/REJECTED) → no-op (sessiz dön)
   *   <li>allLinesFullyShipped && canShip() → SHIPPED
   *   <li>!allLinesFullyShipped && anyLineShipped && canShip() → PARTIALLY_SHIPPED
   *   <li>İkisi de false → değişiklik yok
   * </ul>
   */
  public void recordShipmentProgress(boolean allLinesFullyShipped, boolean anyLineShipped) {
    if (status.isTerminal()) {
      return;
    }

    if (allLinesFullyShipped && status.canShip()) {
      this.status = OrderStatus.SHIPPED;
    } else if (anyLineShipped && status.canShip()) {
      this.status = OrderStatus.PARTIALLY_SHIPPED;
    }
  }

  public Money getGrandTotal() {
    if (totals == null) {
      throw new IllegalStateException("SalesOrder totals cannot be null");
    }
    return totals.calculateGrandTotal();
  }
}
