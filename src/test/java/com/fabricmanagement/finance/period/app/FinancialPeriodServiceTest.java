package com.fabricmanagement.finance.period.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.fx.app.UnrealizedFxService;
import com.fabricmanagement.finance.period.domain.FinancialPeriod;
import com.fabricmanagement.finance.period.domain.FinancialPeriodStatus;
import com.fabricmanagement.finance.period.dto.UnrealizedFxPositionDto;
import com.fabricmanagement.finance.period.infra.repository.FinancialPeriodRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialPeriodServiceTest {

  @Mock private FinancialPeriodRepository financialPeriodRepository;
  @Mock private UnrealizedFxService unrealizedFxService;
  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-30T20:00:00Z"), ZoneOffset.UTC);

  @AfterEach
  void clearTenantContext() {
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.clear();
  }

  @Test
  void reopenPeriodRejectsWhenLaterPeriodIsClosed() {
    UUID tenantId = UUID.randomUUID();
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.setCurrentTenantId(
        tenantId);
    FinancialPeriod may = closedPeriod(tenantId, YearMonth.of(2026, 5));

    when(financialPeriodRepository.findWithLockByTenantIdAndId(tenantId, may.getId()))
        .thenReturn(Optional.of(may));
    when(financialPeriodRepository.existsByTenantIdAndStatusAndEndDateAfter(
            tenantId, FinancialPeriodStatus.CLOSED, may.getEndDate()))
        .thenReturn(true);

    assertThatThrownBy(() -> service().reopenPeriod(may.getId()))
        .isInstanceOf(FinanceDomainException.class)
        .hasMessageContaining("later financial period is already closed");

    verify(unrealizedFxService, never()).reversePeriodEntries(tenantId, may);
  }

  @Test
  void closePeriodRejectsWhenLaterPeriodIsClosed() {
    UUID tenantId = UUID.randomUUID();
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.setCurrentTenantId(
        tenantId);
    FinancialPeriod may = openPeriod(tenantId, YearMonth.of(2026, 5));

    when(financialPeriodRepository.findWithLockByTenantIdAndId(tenantId, may.getId()))
        .thenReturn(Optional.of(may));
    when(financialPeriodRepository.existsByTenantIdAndStatusAndEndDateAfter(
            tenantId, FinancialPeriodStatus.CLOSED, may.getEndDate()))
        .thenReturn(true);

    assertThatThrownBy(() -> service().closePeriod(may.getId()))
        .isInstanceOf(FinanceDomainException.class)
        .hasMessageContaining("later financial period is already closed");

    verify(unrealizedFxService, never())
        .closePeriod(
            org.mockito.ArgumentMatchers.eq(tenantId),
            org.mockito.ArgumentMatchers.eq(may),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void latestClosedPositionUsesNetLedgerStandingPosition() {
    UUID tenantId = UUID.randomUUID();
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.setCurrentTenantId(
        tenantId);
    FinancialPeriod may = closedPeriod(tenantId, YearMonth.of(2026, 5));

    when(financialPeriodRepository.findTopByTenantIdAndStatusOrderByEndDateDesc(
            tenantId, FinancialPeriodStatus.CLOSED))
        .thenReturn(Optional.of(may));
    when(unrealizedFxService.getStandingPosition(tenantId, may.getEndDate()))
        .thenReturn(new UnrealizedFxService.PositionResult(List.of(), new BigDecimal("50.0000")));

    UnrealizedFxPositionDto position = service().getLatestClosedPosition();

    assertThat(position.period().id()).isEqualTo(may.getId());
    assertThat(position.unrealizedFxPosition()).isEqualByComparingTo("50.0000");
  }

  private FinancialPeriodService service() {
    return new FinancialPeriodService(
        financialPeriodRepository, unrealizedFxService, reportingCurrencyPort, clock);
  }

  private FinancialPeriod openPeriod(UUID tenantId, YearMonth month) {
    FinancialPeriod period = FinancialPeriod.forMonth(tenantId, month);
    period.setId(UUID.randomUUID());
    return period;
  }

  private FinancialPeriod closedPeriod(UUID tenantId, YearMonth month) {
    FinancialPeriod period = FinancialPeriod.forMonth(tenantId, month);
    period.setId(UUID.randomUUID());
    period.close(Instant.parse("2026-06-01T10:00:00Z"), UUID.randomUUID());
    return period;
  }
}
