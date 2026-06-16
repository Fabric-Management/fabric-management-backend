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

  /** Maximum acceptable rounding discrepancy between client-supplied and line-derived totals. */
  private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

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
  @Setter(AccessLevel.NONE)
  private InvoiceStatus status = InvoiceStatus.DRAFT;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 30)
  @Builder.Default
  @Setter(AccessLevel.NONE)
  private InvoicePaymentStatus paymentStatus = InvoicePaymentStatus.UNPAID;

  @Column(name = "original_invoice_id")
  private UUID originalInvoiceId;

  @Column(name = "issue_date", nullable = false)
  private LocalDate issueDate;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "payment_date")
  @Setter(AccessLevel.NONE)
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
  private Money taxAmount;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "discount_amount", precision = 19, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", length = 3, insertable = false, updatable = false))
  })
  private Money discountAmount;

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
  @Setter(AccessLevel.NONE)
  private Money amountPaid;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "amount_credited", precision = 19, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", length = 3, insertable = false, updatable = false))
  })
  @Setter(AccessLevel.NONE)
  private Money amountCredited;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "amount_due", precision = 19, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", length = 3, insertable = false, updatable = false))
  })
  @Setter(AccessLevel.NONE)
  private Money amountDue;

  @Column(name = "reporting_currency", length = 3)
  private String reportingCurrency;

  @Column(name = "issue_exchange_rate", precision = 20, scale = 8)
  private BigDecimal issueExchangeRate;

  @Column(name = "issue_exchange_rate_date")
  private LocalDate issueExchangeRateDate;

  @Column(name = "reporting_total", precision = 19, scale = 4)
  private BigDecimal reportingTotal;

  @Transient
  public String getCurrency() {
    if (subtotal == null || subtotal.getCurrency() == null) {
      throw new IllegalStateException("Invoice subtotal or currency cannot be null");
    }
    return subtotal.getCurrency().getCurrencyCode();
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

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
  @OrderBy("lineNumber ASC")
  @Builder.Default
  private List<InvoiceLine> lines = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
  @Builder.Default
  private List<InvoiceTaxLine> taxLines = new ArrayList<>();

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

  /**
   * Derives all header monetary amounts from line-level totals.
   *
   * <p><b>Rounding policy:</b> Line-level arithmetic uses scale 4, HALF_UP (see {@link
   * InvoiceLine#calculate()}). Header {@link Money} values are rounded once at aggregation to the
   * currency's default fraction digits (scale 2 for TRY/USD/EUR) — no per-line rounding to scale 2.
   *
   * <p><b>totalAmount derivation:</b> {@code totalAmount} is computed from the <em>rounded</em>
   * header Money fields ({@code subtotal − discountAmount + taxAmount}), <b>not</b> from {@code Σ
   * lineTotal}. This guarantees internal header consistency required by e-invoice validators (TR
   * e-Fatura). The difference between derived totalAmount and {@code Σ lineTotal} is expected to be
   * at most 1 kuruş due to independent rounding of each component.
   *
   * <p>When lines are present, client-sent header amounts (subtotal, taxAmount, discountAmount) are
   * <b>ignored</b> — all values are derived.
   *
   * <p><b>Status invariant:</b> This method intentionally does <em>not</em> enforce a DRAFT-only
   * guard. Callers (e.g. {@link #issue()}, service layer) are responsible for verifying status
   * before invoking recalculation.
   */
  public void recalculateFromLines() {
    if (lines.isEmpty()) {
      return;
    }

    // Ensure every line's calculated fields are final before we sum.
    // This avoids depending on JPA @PrePersist/@PreUpdate ordering.
    lines.forEach(InvoiceLine::calculate);

    String curr = getCurrency();

    BigDecimal sumSubtotal = BigDecimal.ZERO;
    BigDecimal sumDiscount = BigDecimal.ZERO;

    for (InvoiceLine line : lines) {
      sumSubtotal = sumSubtotal.add(line.getLineSubtotal());
      sumDiscount = sumDiscount.add(line.getLineDiscount());
    }

    this.subtotal = Money.of(sumSubtotal, curr);
    this.discountAmount = Money.of(sumDiscount, curr);

    rebuildTaxLines(curr);

    // Derive totalAmount from rounded header Money fields — guarantees
    // internal header arithmetic consistency (subtotal − discount + tax == total)
    // required by e-invoice validators. May differ from Σ lineTotal by ≤ 0.01
    // due to independent rounding of each component.
    this.totalAmount = this.subtotal.subtract(this.discountAmount).add(this.taxAmount);

    // Recompute amountDue from the corrected totalAmount
    this.amountPaid =
        Money.of((this.amountPaid != null ? this.amountPaid : Money.zero(curr)).getAmount(), curr);
    this.amountCredited =
        Money.of(
            (this.amountCredited != null ? this.amountCredited : Money.zero(curr)).getAmount(),
            curr);
    recomputePaymentStatus(null);
  }

  private void rebuildTaxLines(String currency) {
    // TODO(FIN-5): Optimization - orphanRemoval triggers DELETE for all existing rows on every
    // recalculate.
    // Consider diff-and-update or skipping rebuild if lines haven't changed.
    this.taxLines.clear();

    record TaxGroupKey(TaxCategory category, BigDecimal rate) {
      TaxGroupKey {
        rate = rate != null ? rate.stripTrailingZeros() : BigDecimal.ZERO;
      }
    }

    java.util.Map<TaxGroupKey, java.util.List<InvoiceLine>> grouped = new java.util.HashMap<>();
    for (InvoiceLine line : this.lines) {
      TaxGroupKey key =
          new TaxGroupKey(
              line.getTaxCategory() != null ? line.getTaxCategory() : TaxCategory.STANDARD,
              line.getTaxRate() != null ? line.getTaxRate() : BigDecimal.ZERO);
      grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(line);
    }

    BigDecimal totalTaxAmount = BigDecimal.ZERO;

    for (java.util.Map.Entry<TaxGroupKey, java.util.List<InvoiceLine>> entry : grouped.entrySet()) {
      TaxGroupKey key = entry.getKey();
      BigDecimal groupTaxableBase = BigDecimal.ZERO;
      BigDecimal groupTaxAmount = BigDecimal.ZERO;

      for (InvoiceLine line : entry.getValue()) {
        groupTaxableBase =
            groupTaxableBase.add(line.getLineSubtotal().subtract(line.getLineDiscount()));
        groupTaxAmount = groupTaxAmount.add(line.getLineTax());
      }

      // Round to scale 2
      groupTaxAmount = groupTaxAmount.setScale(2, java.math.RoundingMode.HALF_UP);
      groupTaxableBase = groupTaxableBase.setScale(2, java.math.RoundingMode.HALF_UP);

      InvoiceTaxLine taxLine =
          InvoiceTaxLine.builder()
              .taxCategory(key.category())
              .taxRate(key.rate())
              .taxableBase(groupTaxableBase)
              .taxAmount(groupTaxAmount)
              .build();

      this.taxLines.add(taxLine);
      totalTaxAmount = totalTaxAmount.add(groupTaxAmount);
    }

    this.taxAmount = Money.of(totalTaxAmount, currency);

    // Header taxRate deprecation logic:
    if (grouped.size() == 1) {
      this.taxRate = grouped.keySet().iterator().next().rate();
    } else {
      this.taxRate = null;
    }
  }

  /**
   * Validates that client-supplied amounts match the line-derived amounts.
   *
   * @param clientSubtotal the subtotal sent by the client (nullable)
   * @param clientTotal the totalAmount sent by the client (nullable)
   * @throws FinanceDomainException if the difference exceeds 0.01
   */
  public void validateClientAmounts(BigDecimal clientSubtotal, BigDecimal clientTotal) {
    if (lines.isEmpty()) {
      return;
    }

    if (clientSubtotal != null) {
      BigDecimal derivedSubtotal = this.subtotal.getAmount();
      if (clientSubtotal.subtract(derivedSubtotal).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
        throw new FinanceDomainException(
            String.format(
                "Provided subtotal (%s) does not match line-derived subtotal (%s); "
                    + "tolerance is ±%s",
                clientSubtotal.toPlainString(),
                derivedSubtotal.toPlainString(),
                AMOUNT_TOLERANCE.toPlainString()));
      }
    }

    if (clientTotal != null) {
      BigDecimal derivedTotal = this.totalAmount.getAmount();
      if (clientTotal.subtract(derivedTotal).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
        throw new FinanceDomainException(
            String.format(
                "Provided totalAmount (%s) does not match line-derived total (%s); "
                    + "tolerance is ±%s",
                clientTotal.toPlainString(),
                derivedTotal.toPlainString(),
                AMOUNT_TOLERANCE.toPlainString()));
      }
    }
  }

  public boolean hasLines() {
    return lines != null && !lines.isEmpty();
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
    if (hasLines()) {
      recalculateFromLines();
    } else {
      calculateAmounts();
    }
    this.status = InvoiceStatus.ISSUED;
  }

  public void send() {
    if (!status.canSend()) {
      throw new InvoiceStatusTransitionException(status, InvoiceStatus.SENT);
    }
    this.status = InvoiceStatus.SENT;
  }

  public void applyAllocation(Money allocationAmount, LocalDate paymentDate) {
    this.amountPaid =
        (this.amountPaid != null ? this.amountPaid : Money.zero(getCurrency()))
            .add(allocationAmount);
    recomputePaymentStatus(paymentDate);
  }

  public void reverseAllocation(Money allocationAmount) {
    this.amountPaid = this.amountPaid.subtract(allocationAmount);
    recomputePaymentStatus(null);
  }

  public void applyCredit(Money creditAmount, LocalDate applicationDate) {
    this.amountCredited =
        (this.amountCredited != null ? this.amountCredited : Money.zero(getCurrency()))
            .add(creditAmount);
    recomputePaymentStatus(applicationDate);
  }

  public void reverseCredit(Money creditAmount) {
    this.amountCredited = this.amountCredited.subtract(creditAmount);
    recomputePaymentStatus(null);
  }

  private void recomputePaymentStatus(LocalDate dateForPaidStatus) {
    Money paid = this.amountPaid != null ? this.amountPaid : Money.zero(getCurrency());
    Money credited = this.amountCredited != null ? this.amountCredited : Money.zero(getCurrency());
    this.amountDue = this.totalAmount.subtract(paid).subtract(credited);

    if (this.amountDue.isZero() || this.amountDue.isNegative()) {
      this.paymentStatus = InvoicePaymentStatus.PAID;
      if (dateForPaidStatus != null) {
        this.paymentDate = dateForPaidStatus;
      }
    } else if (paid.isPositive() || credited.isPositive()) {
      this.paymentStatus = InvoicePaymentStatus.PARTIALLY_PAID;
      this.paymentDate = null;
    } else {
      this.paymentStatus = InvoicePaymentStatus.UNPAID;
      this.paymentDate = null;
    }
  }

  public boolean isOverdue(LocalDate referenceDate) {
    return this.dueDate != null
        && referenceDate.isAfter(this.dueDate)
        && this.paymentStatus != InvoicePaymentStatus.PAID
        && this.status != InvoiceStatus.CANCELLED
        && this.status != InvoiceStatus.VOIDED;
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

  public void captureReportingSnapshot(
      String reportingCurrency,
      BigDecimal issueExchangeRate,
      LocalDate issueExchangeRateDate,
      BigDecimal reportingTotal) {
    this.reportingCurrency = reportingCurrency;
    this.issueExchangeRate = issueExchangeRate;
    this.issueExchangeRateDate = issueExchangeRateDate;
    this.reportingTotal = reportingTotal;
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

    this.totalAmount = this.subtotal.subtract(this.discountAmount).add(this.taxAmount);

    Money currentPaid = this.amountPaid != null ? this.amountPaid : Money.zero(curr);
    this.amountPaid = Money.of(currentPaid.getAmount(), curr);

    Money currentCredited = this.amountCredited != null ? this.amountCredited : Money.zero(curr);
    this.amountCredited = Money.of(currentCredited.getAmount(), curr);

    this.amountDue = this.totalAmount.subtract(this.amountPaid).subtract(this.amountCredited);
  }

  public boolean isOverdue() {
    return isOverdue(LocalDate.now());
  }

  public long getDaysOverdue() {
    return getDaysOverdue(LocalDate.now());
  }

  public long getDaysOverdue(LocalDate referenceDate) {
    if (!isOverdue(referenceDate)) {
      return 0;
    }
    return java.time.temporal.ChronoUnit.DAYS.between(dueDate, referenceDate);
  }
}
