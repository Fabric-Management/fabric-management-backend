package com.fabricmanagement.finance.period.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.fx.app.UnrealizedFxService;
import com.fabricmanagement.finance.fx.domain.FxRevaluation;
import com.fabricmanagement.finance.period.domain.FinancialPeriod;
import com.fabricmanagement.finance.period.domain.FinancialPeriodStatus;
import com.fabricmanagement.finance.period.dto.CloseFinancialPeriodResponse;
import com.fabricmanagement.finance.period.dto.FinancialPeriodDto;
import com.fabricmanagement.finance.period.dto.FxRevaluationDto;
import com.fabricmanagement.finance.period.dto.ReopenFinancialPeriodResponse;
import com.fabricmanagement.finance.period.dto.UnrealizedFxPositionDto;
import com.fabricmanagement.finance.period.infra.repository.FinancialPeriodRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FinancialPeriodService {

  private final FinancialPeriodRepository financialPeriodRepository;
  private final UnrealizedFxService unrealizedFxService;
  private final TenantReportingCurrencyPort reportingCurrencyPort;
  private final Clock clock;

  public FinancialPeriodDto ensurePeriod(int year, int month) {
    UUID tenantId = TenantContext.requireTenantId();
    FinancialPeriod period = ensurePeriod(tenantId, YearMonth.of(year, month));
    return FinancialPeriodDto.from(period);
  }

  @Transactional(readOnly = true)
  public FinancialPeriodDto getPeriod(UUID periodId) {
    return FinancialPeriodDto.from(getPeriodOrThrow(TenantContext.requireTenantId(), periodId));
  }

  public CloseFinancialPeriodResponse closePeriod(UUID periodId) {
    UUID tenantId = TenantContext.requireTenantId();
    FinancialPeriod period =
        financialPeriodRepository
            .findWithLockByTenantIdAndId(tenantId, periodId)
            .orElseThrow(() -> new NotFoundException("Financial period not found: " + periodId));

    if (period.getStatus() == FinancialPeriodStatus.CLOSED) {
      List<FxRevaluation> entries = unrealizedFxService.getEntries(tenantId, period.getId());
      return new CloseFinancialPeriodResponse(
          FinancialPeriodDto.from(period),
          0,
          0,
          sum(entries),
          entries.stream().map(FxRevaluationDto::from).toList());
    }
    if (financialPeriodRepository.existsByTenantIdAndStatusAndEndDateAfter(
        tenantId, FinancialPeriodStatus.CLOSED, period.getEndDate())) {
      throw new FinanceDomainException(
          "Cannot close %s; a later financial period is already closed"
              .formatted(period.yearMonth()));
    }

    FinancialPeriod previousClosedPeriod =
        financialPeriodRepository
            .findTopByTenantIdAndStatusAndEndDateBeforeOrderByEndDateDesc(
                tenantId, FinancialPeriodStatus.CLOSED, period.getEndDate())
            .orElse(null);

    UnrealizedFxService.CloseResult result =
        unrealizedFxService.closePeriod(
            tenantId,
            period,
            previousClosedPeriod,
            reportingCurrencyPort.getReportingCurrency(tenantId));

    period.close(Instant.now(clock), currentUserId());
    financialPeriodRepository.save(period);

    return new CloseFinancialPeriodResponse(
        FinancialPeriodDto.from(period),
        result.reversedEntryCount(),
        result.revaluationEntryCount(),
        result.periodMovement(),
        result.entries().stream().map(FxRevaluationDto::from).toList());
  }

  public ReopenFinancialPeriodResponse reopenPeriod(UUID periodId) {
    UUID tenantId = TenantContext.requireTenantId();
    FinancialPeriod period =
        financialPeriodRepository
            .findWithLockByTenantIdAndId(tenantId, periodId)
            .orElseThrow(() -> new NotFoundException("Financial period not found: " + periodId));

    if (period.getStatus() != FinancialPeriodStatus.CLOSED) {
      throw new FinanceDomainException("Only closed financial periods can be reopened");
    }
    if (financialPeriodRepository.existsByTenantIdAndStatusAndEndDateAfter(
        tenantId, FinancialPeriodStatus.CLOSED, period.getEndDate())) {
      throw new FinanceDomainException(
          "Cannot reopen %s; a later financial period is already closed"
              .formatted(period.yearMonth()));
    }

    UnrealizedFxService.ReopenResult result =
        unrealizedFxService.reversePeriodEntries(tenantId, period);
    period.reopen(Instant.now(clock), currentUserId());
    financialPeriodRepository.save(period);

    return new ReopenFinancialPeriodResponse(
        FinancialPeriodDto.from(period),
        result.entries().size(),
        result.reversalMovement(),
        result.entries().stream().map(FxRevaluationDto::from).toList());
  }

  @Transactional(readOnly = true)
  public UnrealizedFxPositionDto getLatestClosedPosition() {
    UUID tenantId = TenantContext.requireTenantId();
    FinancialPeriod period =
        financialPeriodRepository
            .findTopByTenantIdAndStatusOrderByEndDateDesc(tenantId, FinancialPeriodStatus.CLOSED)
            .orElseThrow(() -> new NotFoundException("No closed financial period found"));
    return getPosition(tenantId, period);
  }

  @Transactional(readOnly = true)
  public UnrealizedFxPositionDto getPosition(UUID periodId) {
    UUID tenantId = TenantContext.requireTenantId();
    return getPosition(tenantId, getPeriodOrThrow(tenantId, periodId));
  }

  @Transactional(readOnly = true)
  public List<FxRevaluationDto> getEntries(UUID periodId) {
    UUID tenantId = TenantContext.requireTenantId();
    getPeriodOrThrow(tenantId, periodId);
    return unrealizedFxService.getEntries(tenantId, periodId).stream()
        .map(FxRevaluationDto::from)
        .toList();
  }

  private UnrealizedFxPositionDto getPosition(UUID tenantId, FinancialPeriod period) {
    UnrealizedFxService.PositionResult result =
        unrealizedFxService.getStandingPosition(tenantId, period.getEndDate());
    return new UnrealizedFxPositionDto(
        FinancialPeriodDto.from(period),
        result.position(),
        result.entries().stream().map(FxRevaluationDto::from).toList());
  }

  private FinancialPeriod ensurePeriod(UUID tenantId, YearMonth month) {
    return financialPeriodRepository
        .findByTenantIdAndPeriodYearAndPeriodMonth(tenantId, month.getYear(), month.getMonthValue())
        .orElseGet(() -> financialPeriodRepository.save(FinancialPeriod.forMonth(tenantId, month)));
  }

  private FinancialPeriod getPeriodOrThrow(UUID tenantId, UUID periodId) {
    return financialPeriodRepository
        .findByTenantIdAndId(tenantId, periodId)
        .orElseThrow(() -> new NotFoundException("Financial period not found: " + periodId));
  }

  private BigDecimal sum(List<FxRevaluation> entries) {
    return entries.stream()
        .map(FxRevaluation::getUnrealizedGainLoss)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private UUID currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUserContext context) {
      return context.userId();
    }
    return null;
  }
}
