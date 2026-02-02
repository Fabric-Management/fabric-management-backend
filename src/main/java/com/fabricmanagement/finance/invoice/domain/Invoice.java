package com.fabricmanagement.finance.invoice.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.platform.company.domain.TradingPartner;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Type;

/**
 * Invoice entity with TradingPartner integration.
 *
 * <p>Supports both AR (Accounts Receivable) and AP (Accounts Payable) invoices. Uses
 * trading_partner_id as the primary customer/vendor reference (Faz 1.5 pattern).
 *
 * <h2>TradingPartner Integration:</h2>
 *
 * <ul>
 *   <li>For SALES invoices: partner is the customer (AR)
 *   <li>For PURCHASE invoices: partner is the vendor/supplier (AP)
 * </ul>
 */
@Entity
@Table(
    name = "finance_invoice",
    schema = "finance",
    indexes = {
      @Index(name = "idx_inv_tenant", columnList = "tenant_id"),
      @Index(name = "idx_inv_trading_partner", columnList = "trading_partner_id"),
      @Index(name = "idx_inv_status", columnList = "status"),
      @Index(name = "idx_inv_issue_date", columnList = "issue_date"),
      @Index(name = "idx_inv_due_date", columnList = "due_date")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_inv_tenant_invoice_number",
          columnNames = {"tenant_id", "invoice_number"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice extends BaseEntity {

  // ═══════════════════════════════════════════════════════════════════════════
  // TradingPartner Reference (Faz 1.5)
  // ═══════════════════════════════════════════════════════════════════════════

  /** FK to TradingPartner - customer for AR, vendor for AP. */
  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  /** Lazy-loaded TradingPartner relationship. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trading_partner_id", insertable = false, updatable = false)
  private TradingPartner tradingPartner;

  // ═══════════════════════════════════════════════════════════════════════════
  // Invoice Identification
  // ═══════════════════════════════════════════════════════════════════════════

  /** Unique invoice number per tenant (e.g., INV-20260202-00001). */
  @Column(name = "invoice_number", nullable = false, length = 50)
  private String invoiceNumber;

  /** Reference to related order. */
  @Column(name = "order_reference", length = 100)
  private String orderReference;

  /** External reference (customer PO number, etc.). */
  @Column(name = "external_reference", length = 100)
  private String externalReference;

  /** Invoice type. */
  @Enumerated(EnumType.STRING)
  @Column(name = "invoice_type", nullable = false, length = 20)
  @Builder.Default
  private InvoiceType invoiceType = InvoiceType.SALES;

  /** Invoice status. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  @Builder.Default
  private InvoiceStatus status = InvoiceStatus.DRAFT;

  // ═══════════════════════════════════════════════════════════════════════════
  // Dates
  // ═══════════════════════════════════════════════════════════════════════════

  /** Invoice issue date. */
  @Column(name = "issue_date", nullable = false)
  private LocalDate issueDate;

  /** Payment due date. */
  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  /** Actual payment date (when fully paid). */
  @Column(name = "payment_date")
  private LocalDate paymentDate;

  // ═══════════════════════════════════════════════════════════════════════════
  // Financial
  // ═══════════════════════════════════════════════════════════════════════════

  /** Subtotal (before tax and discount). */
  @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
  private BigDecimal subtotal;

  /** Tax amount. */
  @Column(name = "tax_amount", precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal taxAmount = BigDecimal.ZERO;

  /** Discount amount. */
  @Column(name = "discount_amount", precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal discountAmount = BigDecimal.ZERO;

  /** Total amount (subtotal + tax - discount). */
  @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal totalAmount;

  /** Amount already paid. */
  @Column(name = "amount_paid", precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal amountPaid = BigDecimal.ZERO;

  /** Amount due (totalAmount - amountPaid). */
  @Column(name = "amount_due", precision = 19, scale = 4)
  private BigDecimal amountDue;

  /** Currency code (ISO 4217). */
  @Column(name = "currency", length = 3)
  @Builder.Default
  private String currency = "TRY";

  /** Tax rate percentage. */
  @Column(name = "tax_rate", precision = 5, scale = 2)
  private BigDecimal taxRate;

  // ═══════════════════════════════════════════════════════════════════════════
  // Address
  // ═══════════════════════════════════════════════════════════════════════════

  /** Billing address. */
  @Column(name = "billing_address", length = 500)
  private String billingAddress;

  // ═══════════════════════════════════════════════════════════════════════════
  // Metadata
  // ═══════════════════════════════════════════════════════════════════════════

  /** Notes/comments. */
  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  /** Flexible metadata (payment terms, bank details, etc.). */
  @Type(JsonType.class)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  // ═══════════════════════════════════════════════════════════════════════════
  // BaseEntity
  // ═══════════════════════════════════════════════════════════════════════════

  @Override
  protected String getModuleCode() {
    return "INV";
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Business Methods
  // ═══════════════════════════════════════════════════════════════════════════

  /** Issue the invoice (DRAFT → ISSUED). */
  public void issue() {
    if (status != InvoiceStatus.DRAFT) {
      throw new IllegalStateException("Can only issue invoices in DRAFT status");
    }
    calculateAmounts();
    this.status = InvoiceStatus.ISSUED;
  }

  /** Send the invoice (ISSUED → SENT). */
  public void send() {
    if (status != InvoiceStatus.ISSUED) {
      throw new IllegalStateException("Can only send ISSUED invoices");
    }
    this.status = InvoiceStatus.SENT;
  }

  /** Record a payment. */
  public void recordPayment(BigDecimal amount) {
    if (!status.canReceivePayment()) {
      throw new IllegalStateException("Cannot record payment for invoice in status: " + status);
    }

    this.amountPaid = (this.amountPaid != null ? this.amountPaid : BigDecimal.ZERO).add(amount);
    this.amountDue = this.totalAmount.subtract(this.amountPaid);

    if (this.amountDue.compareTo(BigDecimal.ZERO) <= 0) {
      this.status = InvoiceStatus.PAID;
      this.paymentDate = LocalDate.now();
    } else {
      this.status = InvoiceStatus.PARTIALLY_PAID;
    }
  }

  /** Mark invoice as overdue. */
  public void markOverdue() {
    if (status.isAwaitingPayment()) {
      this.status = InvoiceStatus.OVERDUE;
    }
  }

  /** Cancel the invoice. */
  public void cancel() {
    if (!status.canCancel()) {
      throw new IllegalStateException("Cannot cancel invoice in status: " + status);
    }
    this.status = InvoiceStatus.CANCELLED;
  }

  /** Void the invoice (accounting reversal). */
  public void voidInvoice() {
    if (status.isTerminal()) {
      throw new IllegalStateException("Cannot void invoice in terminal status: " + status);
    }
    this.status = InvoiceStatus.VOIDED;
  }

  /** Calculate totals. */
  public void calculateAmounts() {
    if (this.subtotal == null) {
      this.subtotal = BigDecimal.ZERO;
    }
    if (this.taxAmount == null) {
      this.taxAmount = BigDecimal.ZERO;
    }
    if (this.discountAmount == null) {
      this.discountAmount = BigDecimal.ZERO;
    }

    this.totalAmount = this.subtotal.add(this.taxAmount).subtract(this.discountAmount);
    this.amountDue =
        this.totalAmount.subtract(this.amountPaid != null ? this.amountPaid : BigDecimal.ZERO);
  }

  /** Check if invoice is overdue. */
  public boolean isOverdue() {
    return status.isAwaitingPayment() && LocalDate.now().isAfter(dueDate);
  }

  /** Get days overdue. */
  public long getDaysOverdue() {
    if (!isOverdue()) {
      return 0;
    }
    return java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now());
  }
}
