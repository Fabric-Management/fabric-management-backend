package com.fabricmanagement.sales.ownership.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.sales.ownership.app.OwnershipTriageAgingEmailAdapter;
import com.fabricmanagement.sales.ownership.app.OwnershipTriageProcessor;
import com.fabricmanagement.sales.ownership.domain.OwnershipTriageCase;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class OwnershipTriageDedupIT extends AbstractIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

  @Autowired private OwnershipTriageCaseLogRepository caseLogRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void concurrentInstancesHaveOneNotificationClaimAndOneAgingClaim() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Instant gapStartedAt = NOW.minusSeconds(172_800);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      CompletableFuture<Boolean> first =
          CompletableFuture.supplyAsync(
              () -> claimNotification(tenantId, customerId, gapStartedAt), executor);
      CompletableFuture<Boolean> second =
          CompletableFuture.supplyAsync(
              () -> claimNotification(tenantId, customerId, gapStartedAt), executor);

      assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(true, false);
    }

    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM sales.ownership_triage_case_log
                WHERE tenant_id = ? AND customer_id = ? AND gap_started_at = ?
                """,
                Long.class,
                tenantId,
                customerId,
                java.sql.Timestamp.from(gapStartedAt)))
        .isEqualTo(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      CompletableFuture<Boolean> first =
          CompletableFuture.supplyAsync(
              () -> claimAging(tenantId, customerId, gapStartedAt), executor);
      CompletableFuture<Boolean> second =
          CompletableFuture.supplyAsync(
              () -> claimAging(tenantId, customerId, gapStartedAt), executor);

      assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(true, false);
    }
  }

  @Test
  void anEmailFailureRollsBackTheAgingStamp() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Instant gapStartedAt = NOW.minusSeconds(172_800);
    transactionTemplate.executeWithoutResult(
        ignored ->
            caseLogRepository.tryRecordNotificationRequested(
                tenantId, customerId, gapStartedAt, NOW.minusSeconds(86_400)));

    OwnershipTriageQueryRepository queryRepository = mock(OwnershipTriageQueryRepository.class);
    OwnershipTriageAgingEmailAdapter emailAdapter = mock(OwnershipTriageAgingEmailAdapter.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    OwnershipTriageCase triageCase =
        new OwnershipTriageCase(customerId, "Acme", 1, NOW.minusSeconds(3_600), gapStartedAt, 24);
    when(queryRepository.findAll(tenantId)).thenReturn(List.of(triageCase));
    org.mockito.Mockito.doThrow(new IllegalStateException("outbox unavailable"))
        .when(emailAdapter)
        .queue(tenantId, triageCase);
    OwnershipTriageProcessor processor =
        new OwnershipTriageProcessor(
            queryRepository,
            caseLogRepository,
            emailAdapter,
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    ignored -> processor.processTenant(tenantId)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("outbox unavailable");

    assertThat(
            jdbc.queryForObject(
                """
                SELECT aging_alert_queued_at IS NULL
                FROM sales.ownership_triage_case_log
                WHERE tenant_id = ? AND customer_id = ? AND gap_started_at = ?
                """,
                Boolean.class,
                tenantId,
                customerId,
                java.sql.Timestamp.from(gapStartedAt)))
        .isTrue();
  }

  @Test
  void anEventPublicationFailureRollsBackTheNotificationStamp() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Instant gapStartedAt = NOW.minusSeconds(3_600);
    OwnershipTriageQueryRepository queryRepository = mock(OwnershipTriageQueryRepository.class);
    OwnershipTriageAgingEmailAdapter emailAdapter = mock(OwnershipTriageAgingEmailAdapter.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    OwnershipTriageCase triageCase =
        new OwnershipTriageCase(customerId, "Acme", 0, null, gapStartedAt, 24);
    when(queryRepository.findAll(tenantId)).thenReturn(List.of(triageCase));
    org.mockito.Mockito.doThrow(new IllegalStateException("event outbox unavailable"))
        .when(publisher)
        .publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    OwnershipTriageProcessor processor =
        new OwnershipTriageProcessor(
            queryRepository,
            caseLogRepository,
            emailAdapter,
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    ignored -> processor.processTenant(tenantId)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("event outbox unavailable");

    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM sales.ownership_triage_case_log
                WHERE tenant_id = ? AND customer_id = ? AND gap_started_at = ?
                """,
                Long.class,
                tenantId,
                customerId,
                java.sql.Timestamp.from(gapStartedAt)))
        .isZero();
  }

  private boolean claimNotification(UUID tenantId, UUID customerId, Instant gapStartedAt) {
    return Boolean.TRUE.equals(
        transactionTemplate.execute(
            ignored ->
                caseLogRepository.tryRecordNotificationRequested(
                    tenantId, customerId, gapStartedAt, NOW)));
  }

  private boolean claimAging(UUID tenantId, UUID customerId, Instant gapStartedAt) {
    return Boolean.TRUE.equals(
        transactionTemplate.execute(
            ignored ->
                caseLogRepository.tryMarkAgingAlertQueued(
                    tenantId, customerId, gapStartedAt, NOW)));
  }
}
