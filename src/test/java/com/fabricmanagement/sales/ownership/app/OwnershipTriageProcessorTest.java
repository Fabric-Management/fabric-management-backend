package com.fabricmanagement.sales.ownership.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.sales.ownership.domain.OwnershipTriageCase;
import com.fabricmanagement.sales.ownership.domain.event.CustomerOwnershipTriageOpenedEvent;
import com.fabricmanagement.sales.ownership.infra.repository.OwnershipTriageCaseLogRepository;
import com.fabricmanagement.sales.ownership.infra.repository.OwnershipTriageQueryRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OwnershipTriageProcessorTest {

  private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

  @Mock private OwnershipTriageQueryRepository queryRepository;
  @Mock private OwnershipTriageCaseLogRepository caseLogRepository;
  @Mock private OwnershipTriageAgingEmailAdapter agingEmailAdapter;
  @Mock private ApplicationEventPublisher eventPublisher;

  private OwnershipTriageProcessor processor;
  private UUID tenantId;
  private OwnershipTriageCase triageCase;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    triageCase =
        new OwnershipTriageCase(
            UUID.randomUUID(), "Acme", 2, NOW.minusSeconds(7_200), NOW.minusSeconds(90_000), 24);
    processor =
        new OwnershipTriageProcessor(
            queryRepository,
            caseLogRepository,
            agingEmailAdapter,
            eventPublisher,
            Clock.fixed(NOW, ZoneOffset.UTC));
    org.mockito.Mockito.lenient()
        .when(queryRepository.findAll(tenantId))
        .thenReturn(List.of(triageCase));
    when(caseLogRepository.countUnresolvedConflicts(tenantId)).thenReturn(0L);
  }

  @Test
  void onlyDatabaseWinnersPublishAndQueueAcrossRepeatedRuns() throws NoSuchMethodException {
    Method processTenant = OwnershipTriageProcessor.class.getMethod("processTenant", UUID.class);
    assertThat(processTenant.getAnnotation(Transactional.class)).isNotNull();

    when(caseLogRepository.tryRecordNotificationRequested(
            tenantId, triageCase.customerId(), triageCase.gapStartedAt(), NOW))
        .thenReturn(true, false);
    when(caseLogRepository.tryMarkAgingAlertQueued(
            tenantId, triageCase.customerId(), triageCase.gapStartedAt(), NOW))
        .thenReturn(true, false);

    OwnershipTriageProcessor.ProcessingSummary first = processor.processTenant(tenantId);
    OwnershipTriageProcessor.ProcessingSummary second = processor.processTenant(tenantId);

    assertThat(first.notificationsRequested()).isEqualTo(1);
    assertThat(first.agingAlertsQueued()).isEqualTo(1);
    assertThat(second.notificationsRequested()).isZero();
    assertThat(second.agingAlertsQueued()).isZero();
    ArgumentCaptor<CustomerOwnershipTriageOpenedEvent> eventCaptor =
        ArgumentCaptor.forClass(CustomerOwnershipTriageOpenedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getCustomerId()).isEqualTo(triageCase.customerId());
    verify(agingEmailAdapter).queue(tenantId, triageCase);
  }

  @Test
  void youngCasesNeverAttemptTheAgingClaim() {
    OwnershipTriageCase youngCase =
        new OwnershipTriageCase(
            triageCase.customerId(), "Acme", 0, null, NOW.minusSeconds(3_600), 24);
    when(queryRepository.findAll(tenantId)).thenReturn(List.of(youngCase));
    when(caseLogRepository.tryRecordNotificationRequested(
            tenantId, youngCase.customerId(), youngCase.gapStartedAt(), NOW))
        .thenReturn(false);

    processor.processTenant(tenantId);

    verify(caseLogRepository, never()).tryMarkAgingAlertQueued(any(), any(), any(), any());
    verify(agingEmailAdapter, never()).queue(any(), any());
  }
}
