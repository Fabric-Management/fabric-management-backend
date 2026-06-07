package com.fabricmanagement.human.payroll.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackService;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPack;
import com.fabricmanagement.human.payroll.domain.PayPeriod;
import com.fabricmanagement.human.payroll.domain.PayRun;
import com.fabricmanagement.human.payroll.domain.PayRunStatus;
import com.fabricmanagement.human.payroll.infra.repository.PayRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayRunService {

  private final PayRunRepository payRunRepository;
  private final PayPeriodService payPeriodService;
  private final HrPolicyPackService policyPackService;
  private final Clock clock;

  @Transactional
  public PayRun createRun(String periodCode, UUID initiatedBy) {
    PayPeriod period = payPeriodService.getPeriod(periodCode);
    UUID tenantId = TenantContext.requireTenantId();

    int nextRunNumber =
        payRunRepository
            .findFirstByTenantIdAndPayPeriodOrderByRunNumberDesc(tenantId, period)
            .map(PayRun::getRunNumber)
            .map(number -> number + 1)
            .orElse(1);

    HrPolicyPack policyPack = resolvePolicyPack(period.getCountryCode());

    PayRun payRun =
        PayRun.builder()
            .payPeriod(period)
            .runNumber(nextRunNumber)
            .status(PayRunStatus.CREATED)
            .policyPackCode(policyPack.getPackCode())
            .policyPackVersion(policyPack.getPackVersion())
            .startedAt(Instant.now(clock))
            .initiatedBy(initiatedBy)
            .build();

    payRun.setTenantId(tenantId);
    PayRun saved = payRunRepository.save(payRun);
    log.info(
        "Created pay run: tenantId={}, period={}, runNumber={}",
        tenantId,
        periodCode,
        nextRunNumber);
    return saved;
  }

  @Transactional
  public void updateStatus(UUID payRunId, PayRunStatus newStatus, String note) {
    PayRun payRun =
        payRunRepository
            .findById(payRunId)
            .orElseThrow(() -> new IllegalArgumentException("Pay run not found: " + payRunId));

    switch (newStatus) {
      case IN_PROGRESS -> payRun.markInProgress();
      case VALIDATED -> payRun.markValidated();
      case LOCKED -> payRun.markLocked();
      case COMPLETED -> payRun.markCompleted(Instant.now(clock));
      case FAILED -> payRun.markFailed(note);
      case CANCELLED -> payRun.setStatus(PayRunStatus.CANCELLED);
      default -> throw new IllegalArgumentException("Unsupported status transition: " + newStatus);
    }

    payRunRepository.save(payRun);
    log.info("Updated pay run status: id={}, status={}", payRunId, newStatus);
  }

  public PayRun getPayRun(UUID payRunId) {
    return payRunRepository
        .findWithPeriod(payRunId)
        .orElseThrow(() -> new IllegalArgumentException("Pay run not found: " + payRunId));
  }

  public List<PayRun> listByStatus(PayRunStatus status) {
    return payRunRepository.findByStatus(TenantContext.requireTenantId(), status);
  }

  private HrPolicyPack resolvePolicyPack(String countryCode) {
    UUID tenantId = TenantContext.requireTenantId();
    String normalizedCountry =
        countryCode != null
            ? countryCode.toUpperCase(Locale.ROOT)
            : TenantContext.getCurrentTenantCountry();
    Optional<HrPolicyPack> countryPack =
        policyPackService.findActivePack(tenantId, normalizedCountry);
    if (countryPack.isPresent()) {
      return countryPack.get();
    }
    return policyPackService
        .findActivePack(
            tenantId,
            com.fabricmanagement.human.compliance.localization.domain.HrLocalizationConstants
                .GLOBAL_COUNTRY_CODE)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No active payroll policy pack for country " + normalizedCountry));
  }
}
