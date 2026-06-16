package com.fabricmanagement.finance.period.app;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.finance.period.domain.FinancialPeriod;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import com.fabricmanagement.finance.period.infra.repository.FinancialPeriodRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultFinancialPeriodGuardServiceTest {

  @Mock private FinancialPeriodRepository financialPeriodRepository;

  @Test
  void assertPostingAllowedRejectsClosedPeriod() {
    UUID tenantId = UUID.randomUUID();
    FinancialPeriod period = FinancialPeriod.forMonth(tenantId, YearMonth.of(2026, 5));
    period.close(Instant.parse("2026-06-01T09:00:00Z"), UUID.randomUUID());

    when(financialPeriodRepository.findByTenantIdAndPeriodYearAndPeriodMonth(tenantId, 2026, 5))
        .thenReturn(Optional.of(period));

    assertThatThrownBy(() -> service().assertPostingAllowed(tenantId, LocalDate.of(2026, 5, 15)))
        .isInstanceOf(ClosedFinancialPeriodException.class)
        .hasMessageContaining("closed financial period 2026-05");
  }

  @Test
  void assertPostingAllowedAllowsOpenPeriod() {
    UUID tenantId = UUID.randomUUID();
    FinancialPeriod period = FinancialPeriod.forMonth(tenantId, YearMonth.of(2026, 5));

    when(financialPeriodRepository.findByTenantIdAndPeriodYearAndPeriodMonth(tenantId, 2026, 5))
        .thenReturn(Optional.of(period));

    assertThatCode(() -> service().assertPostingAllowed(tenantId, LocalDate.of(2026, 5, 15)))
        .doesNotThrowAnyException();
  }

  @Test
  void assertPostingAllowedAllowsMissingPeriodWithoutCreatingOne() {
    UUID tenantId = UUID.randomUUID();

    when(financialPeriodRepository.findByTenantIdAndPeriodYearAndPeriodMonth(tenantId, 2026, 5))
        .thenReturn(Optional.empty());

    assertThatCode(() -> service().assertPostingAllowed(tenantId, LocalDate.of(2026, 5, 15)))
        .doesNotThrowAnyException();

    verify(financialPeriodRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verify(financialPeriodRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  private DefaultFinancialPeriodGuardService service() {
    return new DefaultFinancialPeriodGuardService(financialPeriodRepository);
  }
}
