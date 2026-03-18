package com.fabricmanagement.sales.quote.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.offline.domain.OfflineMetadata;
import io.hypersistence.utils.hibernate.type.json.JsonType;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "quote", schema = "sales")
@Getter
@Setter
@NoArgsConstructor
public class Quote extends BaseEntity {

  @Column(name = "quote_number", nullable = false, unique = true, length = 50)
  private String quoteNumber;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "assigned_to_id", nullable = false)
  private UUID assignedToId;

  @Column(name = "module_type", nullable = false, length = 50)
  private String moduleType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private QuoteStatus status = QuoteStatus.DRAFT;

  @Column(name = "estimated_unit_cost", precision = 18, scale = 4)
  private BigDecimal estimatedUnitCost;

  @Column(name = "valid_until", nullable = false)
  private LocalDate validUntil;

  @Column(name = "payment_terms", length = 50)
  private String paymentTerms;

  @Column(name = "lead_time_days")
  private Integer leadTimeDays;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String attachments = "[]";

  @Column(name = "revision_number", nullable = false)
  private Integer revisionNumber = 1;

  @Column(name = "parent_quote_id")
  private UUID parentQuoteId;

  @Embedded private OfflineMetadata offlineMetadata;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "quote_id", nullable = false)
  private List<QuoteLine> lines = new ArrayList<>();

  public void addLine(QuoteLine line) {
    this.lines.add(line);
  }

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "SALES";
  }
}
