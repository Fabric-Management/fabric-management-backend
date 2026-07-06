package com.fabricmanagement.sales.quote.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuoteRetentionPurgeJobTest {

  private static final Instant NOW = Instant.parse("2026-07-05T03:30:00Z");
  private static final UUID TENANT_A = UUID.randomUUID();
  private static final UUID TENANT_B = UUID.randomUUID();

  @Mock private SystemTransactionExecutor systemExecutor;
  @Mock private JdbcTemplate jdbcTemplate;

  private QuoteRetentionPurgeJob job;

  @BeforeEach
  void setUp() {
    job = new QuoteRetentionPurgeJob(systemExecutor, Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(job, "abandonedDraftDays", 90);
    ReflectionTestUtils.setField(job, "deadTokenDays", 365);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void disabledJobDoesNotQueryTenants() {
    ReflectionTestUtils.setField(job, "enabled", false);

    job.purgeQuotesAndTokens();

    verify(systemExecutor, never()).executeQuery(any(), any());
    verify(systemExecutor, never()).executeInTransaction(any());
  }

  @Test
  void scheduledJobIteratesAllActiveTenantsInTenantContext() {
    ReflectionTestUtils.setField(job, "enabled", true);
    when(systemExecutor.executeQuery(eq(QuoteRetentionPurgeJob.ACTIVE_TENANTS_SQL), any()))
        .thenReturn(List.of(TENANT_A, TENANT_B));
    when(systemExecutor.executeInTransaction(any())).thenAnswer(invocation -> runSql(invocation));
    when(jdbcTemplate.update(any(String.class), any(), any(Timestamp.class))).thenReturn(1);

    job.purgeQuotesAndTokens();

    verify(systemExecutor, times(2)).executeInTransaction(any());
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }

  @Test
  void purgeTenantUsesRetentionPredicatesAndThresholds() {
    when(systemExecutor.executeInTransaction(any())).thenAnswer(invocation -> runSql(invocation));
    when(jdbcTemplate.update(any(String.class), any(), any(Timestamp.class))).thenReturn(1);

    QuoteRetentionPurgeJob.PurgeCounts counts = job.purgeTenant(TENANT_A, NOW);

    assertThat(counts.abandonedDraftQuotes()).isEqualTo(1);
    assertThat(counts.deadApprovalTokens()).isEqualTo(1);
    assertThat(QuoteRetentionPurgeJob.DELETE_ABANDONED_DRAFT_QUOTES_SQL)
        .contains("q.status = 'DRAFT'")
        .contains("q.updated_at < ?")
        .contains("NOT EXISTS")
        .contains("sales.quote_line");
    assertThat(QuoteRetentionPurgeJob.DELETE_DEAD_APPROVAL_TOKENS_SQL)
        .contains("t.status IN ('EXPIRED', 'REVOKED')")
        .contains("t.used_at IS NULL")
        .contains("t.created_at < ?");

    ArgumentCaptor<Timestamp> thresholdCaptor = ArgumentCaptor.forClass(Timestamp.class);
    verify(jdbcTemplate)
        .update(
            eq(QuoteRetentionPurgeJob.DELETE_ABANDONED_DRAFT_QUOTES_SQL),
            eq(TENANT_A),
            thresholdCaptor.capture());
    assertThat(thresholdCaptor.getValue().toInstant()).isEqualTo(NOW.minusSeconds(90L * 86400));
  }

  @SuppressWarnings("unchecked")
  private Object runSql(org.mockito.invocation.InvocationOnMock invocation) {
    Function<JdbcTemplate, ?> work = invocation.getArgument(0);
    return work.apply(jdbcTemplate);
  }
}
