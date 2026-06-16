package com.fabricmanagement.finance.period.app;

import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.FinancialPeriodStatus;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import com.fabricmanagement.finance.period.infra.repository.FinancialPeriodRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultFinancialPeriodGuardService implements FinancialPeriodGuard {

  private final FinancialPeriodRepository financialPeriodRepository;

  @Override
  @Transactional(readOnly = true)
  public void assertPostingAllowed(UUID tenantId, LocalDate accountingDate) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(accountingDate, "accountingDate must not be null");

    YearMonth periodMonth = YearMonth.from(accountingDate);
    financialPeriodRepository
        .findByTenantIdAndPeriodYearAndPeriodMonth(
            tenantId, periodMonth.getYear(), periodMonth.getMonthValue())
        .filter(period -> period.getStatus() == FinancialPeriodStatus.CLOSED)
        .ifPresent(
            period -> {
              throw new ClosedFinancialPeriodException(accountingDate, period.yearMonth());
            });
  }
}
