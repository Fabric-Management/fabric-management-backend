package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.sales.ownership.domain.OwnershipTriageCase;
import com.fabricmanagement.sales.ownership.domain.event.CustomerOwnershipTriageOpenedEvent;
import com.fabricmanagement.sales.ownership.infra.repository.OwnershipTriageCaseLogRepository;
import com.fabricmanagement.sales.ownership.infra.repository.OwnershipTriageQueryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OwnershipTriageProcessor {

  private final OwnershipTriageQueryRepository queryRepository;
  private final OwnershipTriageCaseLogRepository caseLogRepository;
  private final OwnershipTriageAgingEmailAdapter agingEmailAdapter;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public ProcessingSummary processTenant(UUID tenantId) {
    Instant now = Instant.now(clock);
    List<OwnershipTriageCase> cases = queryRepository.findAll(tenantId);
    int notificationsRequested = 0;
    int agingAlertsQueued = 0;

    for (OwnershipTriageCase triageCase : cases) {
      if (caseLogRepository.tryRecordNotificationRequested(
          tenantId, triageCase.customerId(), triageCase.gapStartedAt(), now)) {
        notificationsRequested++;
        eventPublisher.publishEvent(
            new CustomerOwnershipTriageOpenedEvent(
                tenantId,
                triageCase.customerId(),
                triageCase.customerName(),
                triageCase.gapStartedAt(),
                triageCase.unassignedOpenQuoteCount()));
      }

      long ageHours = Math.max(0, Duration.between(triageCase.gapStartedAt(), now).toHours());
      if (ageHours >= triageCase.agingThresholdHours()
          && caseLogRepository.tryMarkAgingAlertQueued(
              tenantId, triageCase.customerId(), triageCase.gapStartedAt(), now)) {
        agingEmailAdapter.queue(tenantId, triageCase);
        agingAlertsQueued++;
      }
    }

    long conflicts = caseLogRepository.countUnresolvedConflicts(tenantId);
    if (conflicts > 0) {
      log.warn(
          "Ownership triage derived query disagrees with unresolved case log: tenantId={}, count={}",
          tenantId,
          conflicts);
    }

    long oldestAgeHours =
        cases.stream()
            .mapToLong(
                triageCase ->
                    Math.max(0, Duration.between(triageCase.gapStartedAt(), now).toHours()))
            .max()
            .orElse(0);
    return new ProcessingSummary(
        cases.size(), oldestAgeHours, conflicts, notificationsRequested, agingAlertsQueued);
  }

  public record ProcessingSummary(
      long openCases,
      long oldestAgeHours,
      long caseLogConflicts,
      int notificationsRequested,
      int agingAlertsQueued) {}
}
