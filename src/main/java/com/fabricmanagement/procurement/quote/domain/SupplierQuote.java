package com.fabricmanagement.procurement.quote.domain;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "supplier_quote", schema = "procurement")
@Getter
@Setter
@NoArgsConstructor
public class SupplierQuote extends BaseEntity {

  @Column(name = "quote_number", nullable = false, unique = true, length = 50)
  private String quoteNumber;

  @Column(name = "rfq_id", nullable = false)
  private UUID rfqId;

  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private SupplierQuoteStatus status = SupplierQuoteStatus.RECEIVED;

  @Enumerated(EnumType.STRING)
  @Column(name = "module_type", nullable = false, length = 50)
  private SupplierQuoteModuleType moduleType = SupplierQuoteModuleType.GENERIC;

  @Column(name = "valid_until", nullable = false)
  private LocalDate validUntil;

  @Column(name = "currency", nullable = false, length = 10)
  private String currency;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "originalAmount",
        column = @Column(name = "reporting_total_original", precision = 18, scale = 4)),
    @AttributeOverride(
        name = "originalCurrency",
        column = @Column(name = "reporting_total_orig_currency", length = 3)),
    @AttributeOverride(
        name = "convertedAmount",
        column = @Column(name = "reporting_total_converted", precision = 18, scale = 4)),
    @AttributeOverride(
        name = "convertedCurrency",
        column = @Column(name = "reporting_currency", length = 3)),
    @AttributeOverride(
        name = "exchangeRate",
        column = @Column(name = "reporting_exchange_rate", precision = 20, scale = 8)),
    @AttributeOverride(name = "rateDate", column = @Column(name = "reporting_rate_date"))
  })
  private ConvertedMoney reportingTotal;

  @Column(name = "payment_terms", length = 50)
  private String paymentTerms;

  @Column(name = "lead_time_days")
  private Integer leadTimeDays;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_method", nullable = false, length = 30)
  private QuoteEntryMethod entryMethod;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Type(JsonType.class)
  @Column(name = "attachments", columnDefinition = "jsonb", nullable = false)
  private List<Map<String, Object>> attachments = new ArrayList<>();

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "supplier_quote_id", nullable = false, updatable = false)
  private List<SupplierQuoteLine> lines = new ArrayList<>();

  /** Sadece RECEIVED durumundaki teklife satır eklenebilir. */
  public void addLine(SupplierQuoteLine line) {
    if (this.status != SupplierQuoteStatus.RECEIVED) {
      throw new IllegalStateException("Cannot add line to quote in status: " + this.status);
    }
    this.lines.add(line);
  }

  public void transitionTo(SupplierQuoteStatus target) {
    if (!this.status.canTransitionTo(target)) {
      throw new IllegalStateException(
          String.format("Cannot transition quote from %s to %s", this.status, target));
    }
    this.status = target;
  }

  public void accept() {
    transitionTo(SupplierQuoteStatus.ACCEPTED);
  }

  public void reject() {
    transitionTo(SupplierQuoteStatus.REJECTED);
  }

  public void startReview() {
    transitionTo(SupplierQuoteStatus.UNDER_REVIEW);
  }

  public void expire() {
    transitionTo(SupplierQuoteStatus.EXPIRED);
  }

  public boolean isExpired() {
    return this.validUntil != null && this.validUntil.isBefore(LocalDate.now());
  }

  public BigDecimal computeTotalAmount() {
    if (this.reportingTotal != null) {
      return this.reportingTotal.getOriginalAmount();
    }
    return this.lines.stream()
        .map(SupplierQuoteLine::lineTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Override
  public String getModuleCode() {
    return "PROCUREMENT";
  }
}
