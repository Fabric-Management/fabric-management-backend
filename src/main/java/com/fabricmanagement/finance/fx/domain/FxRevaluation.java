package com.fabricmanagement.finance.fx.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.util.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "fx_revaluation",
    schema = "finance",
    indexes = {
      @Index(name = "idx_fxrev_tenant", columnList = "tenant_id"),
      @Index(name = "idx_fxrev_period", columnList = "period_id"),
      @Index(name = "idx_fxrev_invoice", columnList = "invoice_id"),
      @Index(name = "idx_fxrev_reversal", columnList = "reversal_of_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FxRevaluation extends BaseEntity {

  @Column(name = "period_id", nullable = false)
  private UUID periodId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 30)
  private FxRevaluationEntryType entryType;

  @Column(name = "invoice_side", nullable = false, length = 30)
  private String invoiceSide;

  @Column(name = "as_of_date", nullable = false)
  private LocalDate asOfDate;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column =
            @Column(name = "open_document_amount", nullable = false, precision = 19, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "document_currency", nullable = false, length = 3))
  })
  private Money openDocumentAmount;

  @Column(name = "reporting_currency", nullable = false, length = 3)
  private String reportingCurrency;

  @Column(name = "issue_exchange_rate", nullable = false, precision = 20, scale = 8)
  private BigDecimal issueExchangeRate;

  @Column(name = "issue_exchange_rate_date", nullable = false)
  private LocalDate issueExchangeRateDate;

  @Column(name = "closing_exchange_rate", nullable = false, precision = 20, scale = 8)
  private BigDecimal closingExchangeRate;

  @Column(name = "closing_exchange_rate_date", nullable = false)
  private LocalDate closingExchangeRateDate;

  @Column(name = "unrealized_gain_loss", nullable = false, precision = 19, scale = 4)
  private BigDecimal unrealizedGainLoss;

  @Column(name = "revalued_at", nullable = false)
  private Instant revaluedAt;

  @Column(name = "reversal_of_id")
  private UUID reversalOfId;

  public FxRevaluation reversal(UUID postingPeriodId, Instant reversedAt) {
    FxRevaluation reversal =
        FxRevaluation.builder()
            .periodId(postingPeriodId)
            .invoiceId(invoiceId)
            .entryType(FxRevaluationEntryType.REVERSAL)
            .invoiceSide(invoiceSide)
            .asOfDate(asOfDate)
            .openDocumentAmount(openDocumentAmount)
            .reportingCurrency(reportingCurrency)
            .issueExchangeRate(issueExchangeRate)
            .issueExchangeRateDate(issueExchangeRateDate)
            .closingExchangeRate(closingExchangeRate)
            .closingExchangeRateDate(closingExchangeRateDate)
            .unrealizedGainLoss(unrealizedGainLoss.negate())
            .revaluedAt(reversedAt)
            .reversalOfId(getId())
            .build();
    reversal.setTenantId(getTenantId());
    return reversal;
  }

  @Override
  protected String getModuleCode() {
    return "FXU";
  }
}
