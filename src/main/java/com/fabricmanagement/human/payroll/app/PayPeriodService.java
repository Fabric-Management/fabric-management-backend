package com.fabricmanagement.human.payroll.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.payroll.domain.PayPeriod;
import com.fabricmanagement.human.payroll.domain.PayPeriodStatus;
import com.fabricmanagement.human.payroll.infra.repository.PayPeriodRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayPeriodService {

  private final PayPeriodRepository payPeriodRepository;
  private final Clock clock;

  @Transactional
  public PayPeriod createPeriod(
      String periodCode,
      String countryCode,
      LocalDate startDate,
      LocalDate endDate,
      String frequency) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    String normalizedCode = periodCode.toUpperCase(Locale.ROOT);

    payPeriodRepository
        .findByCode(tenantId, normalizedCode)
        .ifPresent(
            existing -> {
              throw new IllegalStateException("Pay period already exists: " + normalizedCode);
            });

    PayPeriod period =
        PayPeriod.builder()
            .periodCode(normalizedCode)
            .countryCode(countryCode.toUpperCase(Locale.ROOT))
            .startDate(startDate)
            .endDate(endDate)
            .frequency(frequency)
            .status(PayPeriodStatus.DRAFT)
            .build();
    period.setTenantId(tenantId);

    PayPeriod saved = payPeriodRepository.save(period);
    log.info("Created pay period: tenantId={}, code={}", tenantId, normalizedCode);
    return saved;
  }

  @Transactional
  public PayPeriod openPeriod(String periodCode) {
    PayPeriod period = requirePeriod(periodCode);
    period.open();
    return payPeriodRepository.save(period);
  }

  @Transactional
  public PayPeriod lockPeriod(String periodCode) {
    PayPeriod period = requirePeriod(periodCode);
    period.lock(TenantContext.getCurrentUserId(), Instant.now(clock));
    return payPeriodRepository.save(period);
  }

  public PayPeriod getPeriod(String periodCode) {
    return requirePeriod(periodCode);
  }

  public List<PayPeriod> listOpenPeriods(String countryCode) {
    return payPeriodRepository.findByStatus(
        TenantContext.getCurrentTenantId(),
        countryCode != null ? countryCode.toUpperCase(Locale.ROOT) : localizationCountry(),
        PayPeriodStatus.OPEN);
  }

  public PayPeriod findCoveringDate(LocalDate date, String countryCode) {
    return payPeriodRepository
        .findPeriodCoveringDate(
            TenantContext.getCurrentTenantId(),
            countryCode != null ? countryCode.toUpperCase(Locale.ROOT) : localizationCountry(),
            date)
        .orElseThrow(() -> new IllegalStateException("No pay period covering date: " + date));
  }

  private PayPeriod requirePeriod(String periodCode) {
    return payPeriodRepository
        .findByCode(TenantContext.getCurrentTenantId(), periodCode.toUpperCase(Locale.ROOT))
        .orElseThrow(() -> new IllegalArgumentException("Pay period not found: " + periodCode));
  }

  private String localizationCountry() {
    return TenantContext.getCurrentTenantCountry();
  }
}
