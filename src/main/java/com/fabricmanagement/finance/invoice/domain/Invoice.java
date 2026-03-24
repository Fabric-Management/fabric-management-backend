package com.fabricmanagement.finance.invoice.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.common.exception.InvoiceStatusTransitionException;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Type;

@Entity
@Table(
    name = "finance_invoice",
    schema = "finance",
    indexes = {
      @Index(name = "idx_inv_tenant", columnList = "tenant_id"),
      @Index(name = "idx_inv_trading_partner", columnList = "trading_partner_id"),
      @Index(name = "idx_inv_status", columnList = "status"),
      @Index(name = "idx_inv_issue_date", columnList = "issue_date"),
      @Index(name = "idx_inv_due_date", columnList = "due_date"),
      @Index(name = "idx_inv_original", columnList = "original_invoice_id")
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

  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  @Column(name = "invoice_number", nullable = false, length = 50)
  private String invoiceNumber;

  @Column(name = "order_reference", length = 100)
  private String orderReference;

  @Column(name = "external_reference", length = 100)
  private String externalReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "invoice_type", nullable = false, length = 20)
  @Builder.Default
  private InvoiceType invoiceType = InvoiceType.SALES;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  @Builder.Default
  private InvoiceStatus status = InvoiceStatus.DRAFT;

  @Column(name = "original_invoice_id")
  private UUID originalInvoiceId;

  @Column(name = "issue_date", nullable = false)
  private LocalDate issueDate;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "payment_date")
  private LocalDate paymentDate;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)),
    @AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3))
  })
  private Money subtotal;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "tax_amount", precision = 19, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", length = 3, insertable = false, updatable = false))
  })
  @Builder.Default
  private Money taxAmount = Money.zero("TRY");

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "discount_amount", precision = 19, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", length = 3, insertable = false, updatable = false))
  })
  @Builder.Default
  private Money discountAmount = Money.zero("TRY");

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", length = 3, insertable = false, updatable = false))
  })
  private Money totalAmount;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "amount_paid", precision = 19, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", length = 3, insertable = false, updatable = false))
  })
  @Builder.Default
  private Money amountPaid = Money.zero("TRY");

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "amount_due", precision = 19, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", length = 3, insertable = false, updatable = false))
  })
  private Money amountDue;

  @Transient
  public String getCurrency() {
    return subtotal != null && subtotal.getCurrency() != null
        ? subtotal.getCurrency().getCurrencyCode()
        : "TRY";
  }

  @Column(name = "tax_rate", precision = 5, scale = 2)
  private BigDecimal taxRate;

  @Column(name = "billing_address", length = 500)
  private String billingAddress;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Type(JsonType.class)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @OneToMany(mappedBy = "invoiceId", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("lineNumber ASC")
  @Builder.Default
  private List<InvoiceLine> lines = new ArrayList<>();

  @Override
  protected String getModuleCode() {
    return "INV";
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Line Management
  // ═══════════════════════════════════════════════════════════════════════════

  public void addLine(InvoiceLine line) {
    if (status != InvoiceStatus.DRAFT) {
      throw new FinanceDomainException("Can only add lines to invoices in DRAFT status");
    }
    line.setInvoiceId(this.getId());
    if (line.getLineNumber() == null) {
      line.setLineNumber(lines.size() + 1);
    }
    lines.add(line);
  }

  public void removeLine(UUID lineId) {
    if (status != InvoiceStatus.DRAFT) {
      throw new FinanceDomainException("Can only remove lines from invoices in DRAFT status");
    }
    lines.removeIf(line -> line.getId().equals(lineId));
    resequenceLines();
  }

  public void recalculateFromLines() {
    if (lines.isEmpty()) {
      return;
    }
    String curr = getCurrency();
    BigDecimal newSubtotal =
        lines.stream().map(InvoiceLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    this.subtotal = Money.of(newSubtotal, curr);
    calculateAmounts();
  }

  private void resequenceLines() {
    for (int i = 0; i < lines.size(); i++) {
      lines.get(i).setLineNumber(i + 1);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Lifecycle Methods
  // ═══════════════════════════════════════════════════════════════════════════

  public void issue() {
    if (!status.canIssue()) {
      throw new InvoiceStatusTransitionException(status, InvoiceStatus.ISSUED);
    }
    calculateAmounts();
    this.status = InvoiceStatus.ISSUED;
  }

  public void send() {
    if (!status.canSend()) {
      throw new InvoiceStatusTransitionException(status, InvoiceStatus.SENT);
    }
    this.status = InvoiceStatus.SENT;
  }

  public void recordPayment(Money amount) {
    if (!status.canReceivePayment()) {
      throw new InvoiceStatusTransitionException(
          status, InvoiceStatus.PARTIALLY_PAID, "Cannot record payment in current status");
    }

    this.amountPaid =
        (this.amountPaid != null ? this.amountPaid : Money.zero(getCurrency())).add(amount);
    this.amountDue = this.totalAmount.subtract(this.amountPaid);

    if (this.amountDue.isZero() || this.amountDue.isNegative()) {
      this.status = InvoiceStatus.PAID;
      this.paymentDate = LocalDate.now();
    } else {
      this.status = InvoiceStatus.PARTIALLY_PAID;
    }
  }

  public void markOverdue() {
    if (status.isAwaitingPayment()) {
      this.status = InvoiceStatus.OVERDUE;
    }
  }

  public void cancel() {
    if (!status.canCancel()) {
      throw new InvoiceStatusTransitionException(status, InvoiceStatus.CANCELLED);
    }
    this.status = InvoiceStatus.CANCELLED;
  }

  public void voidInvoice() {
    if (!status.canVoid()) {
      throw new InvoiceStatusTransitionException(status, InvoiceStatus.VOIDED);
    }
    this.status = InvoiceStatus.VOIDED;
  }

  public void dispute() {
    if (!status.canDispute()) {
      throw new InvoiceStatusTransitionException(status, InvoiceStatus.DISPUTED);
    }
    this.status = InvoiceStatus.DISPUTED;
  }

  public void resolveDispute() {
    if (!status.canResolveDispute()) {
      throw new InvoiceStatusTransitionException(status, InvoiceStatus.SENT);
    }
    this.status = InvoiceStatus.SENT;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Calculations
  // ═══════════════════════════════════════════════════════════════════════════

  public void calculateAmounts() {
    String curr = getCurrency();
    if (this.subtotal == null) {
      this.subtotal = Money.zero(curr);
    }
    if (this.taxAmount == null) {
      this.taxAmount = Money.zero(curr);
    }
    if (this.discountAmount == null) {
      this.discountAmount = Money.zero(curr);
    }

    // Force all moneys to have the same currency as subtotal to avoid currency mismatch in
    // calculations
    this.taxAmount = Money.of(this.taxAmount.getAmount(), curr);
    this.discountAmount = Money.of(this.discountAmount.getAmount(), curr);

    this.totalAmount = this.subtotal.add(this.taxAmount).subtract(this.discountAmount);

    Money currentPaid = this.amountPaid != null ? this.amountPaid : Money.zero(curr);
    this.amountPaid = Money.of(currentPaid.getAmount(), curr);
    this.amountDue = this.totalAmount.subtract(this.amountPaid);
  }

  public boolean isOverdue() {
    return status.isAwaitingPayment() && LocalDate.now().isAfter(dueDate);
  }

  public long getDaysOverdue() {
    if (!isOverdue()) {
      return 0;
    }
    return java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now());
  }
}
