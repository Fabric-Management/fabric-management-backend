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
    name = "fx_realization",
    schema = "finance",
    indexes = {
      @Index(name = "idx_fxr_tenant", columnList = "tenant_id"),
      @Index(name = "idx_fxr_invoice", columnList = "invoice_id"),
      @Index(name = "idx_fxr_source", columnList = "source_type, source_id"),
      @Index(name = "idx_fxr_reversal", columnList = "reversal_of_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FxRealization extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 40)
  private FxRealizationSourceType sourceType;

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "document_amount", nullable = false, precision = 19, scale = 4)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "document_currency", nullable = false, length = 3))
  })
  private Money documentAmount;

  @Column(name = "reporting_currency", nullable = false, length = 3)
  private String reportingCurrency;

  @Column(name = "issue_exchange_rate", nullable = false, precision = 20, scale = 8)
  private BigDecimal issueExchangeRate;

  @Column(name = "issue_exchange_rate_date", nullable = false)
  private LocalDate issueExchangeRateDate;

  @Column(name = "settlement_exchange_rate", nullable = false, precision = 20, scale = 8)
  private BigDecimal settlementExchangeRate;

  @Column(name = "settlement_exchange_rate_date", nullable = false)
  private LocalDate settlementExchangeRateDate;

  @Column(name = "realized_gain_loss", nullable = false, precision = 19, scale = 4)
  private BigDecimal realizedGainLoss;

  @Column(name = "realized_at", nullable = false)
  private Instant realizedAt;

  @Column(name = "reversal_of_id")
  private UUID reversalOfId;

  public FxRealization reversal(Instant reversedAt) {
    FxRealization reversal =
        FxRealization.builder()
            .sourceType(sourceType)
            .sourceId(sourceId)
            .invoiceId(invoiceId)
            .documentAmount(documentAmount)
            .reportingCurrency(reportingCurrency)
            .issueExchangeRate(issueExchangeRate)
            .issueExchangeRateDate(issueExchangeRateDate)
            .settlementExchangeRate(settlementExchangeRate)
            .settlementExchangeRateDate(settlementExchangeRateDate)
            .realizedGainLoss(realizedGainLoss.negate())
            .realizedAt(reversedAt)
            .reversalOfId(getId())
            .build();
    reversal.setTenantId(getTenantId());
    return reversal;
  }

  @Override
  protected String getModuleCode() {
    return "FXR";
  }
}
