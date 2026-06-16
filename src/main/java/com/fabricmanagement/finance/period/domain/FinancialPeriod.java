package com.fabricmanagement.finance.period.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "financial_period",
    schema = "finance",
    indexes = {
      @Index(name = "idx_fin_period_tenant", columnList = "tenant_id"),
      @Index(name = "idx_fin_period_status", columnList = "status"),
      @Index(name = "idx_fin_period_end", columnList = "end_date")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_fin_period_tenant_month",
          columnNames = {"tenant_id", "period_year", "period_month"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FinancialPeriod extends BaseEntity {

  @Column(name = "period_year", nullable = false)
  private int periodYear;

  @Column(name = "period_month", nullable = false)
  private int periodMonth;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private FinancialPeriodStatus status = FinancialPeriodStatus.OPEN;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "closed_by")
  private UUID closedBy;

  @Column(name = "reopened_at")
  private Instant reopenedAt;

  @Column(name = "reopened_by")
  private UUID reopenedBy;

  public static FinancialPeriod forMonth(UUID tenantId, YearMonth month) {
    FinancialPeriod period =
        FinancialPeriod.builder()
            .periodYear(month.getYear())
            .periodMonth(month.getMonthValue())
            .startDate(month.atDay(1))
            .endDate(month.atEndOfMonth())
            .status(FinancialPeriodStatus.OPEN)
            .build();
    period.setTenantId(tenantId);
    return period;
  }

  public YearMonth yearMonth() {
    return YearMonth.of(periodYear, periodMonth);
  }

  public void close(Instant closedAt, UUID closedBy) {
    if (status == FinancialPeriodStatus.CLOSED) {
      return;
    }
    status = FinancialPeriodStatus.CLOSED;
    this.closedAt = closedAt;
    this.closedBy = closedBy;
  }

  public void reopen(Instant reopenedAt, UUID reopenedBy) {
    if (status != FinancialPeriodStatus.CLOSED) {
      throw new FinanceDomainException("Only closed financial periods can be reopened");
    }
    status = FinancialPeriodStatus.OPEN;
    this.reopenedAt = reopenedAt;
    this.reopenedBy = reopenedBy;
  }

  @Override
  protected String getModuleCode() {
    return "FP";
  }
}
