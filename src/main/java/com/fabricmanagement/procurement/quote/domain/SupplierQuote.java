package com.fabricmanagement.procurement.quote.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

  @Column(name = "valid_until", nullable = false)
  private LocalDate validUntil;

  @Column(name = "currency", nullable = false, length = 10)
  private String currency = "TRY";

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
  private String attachments = "[]";

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "supplier_quote_id", nullable = false, updatable = false)
  private List<SupplierQuoteLine> lines = new ArrayList<>();

  public void addLine(SupplierQuoteLine line) {
    this.lines.add(line);
  }

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "PROCUREMENT";
  }
}
