package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TrialLifecycleServiceTest {

  @Mock private SystemTransactionExecutor systemExecutor;
  @Mock private CacheManager cacheManager;

  private TrialLifecycleService service;

  @BeforeEach
  void setUp() {
    service = new TrialLifecycleService(systemExecutor, cacheManager);
    ReflectionTestUtils.setField(service, "baseDays", 90);
    ReflectionTestUtils.setField(service, "dormancyWindowDays", 90);
    ReflectionTestUtils.setField(service, "hardCapMonths", 18);
  }

  @Test
  @DisplayName("self-service activation starts trial only for unactivated registered trials")
  void shouldStartSelfServiceTrialIfNeeded() {
    UUID tenantId = UUID.randomUUID();
    when(systemExecutor.executeUpdate(anyString(), any(), any(), any(), eq(tenantId)))
        .thenReturn(1);

    int affected = service.startSelfServiceTrialIfNeeded(tenantId);

    assertThat(affected).isEqualTo(1);
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(systemExecutor).executeUpdate(sqlCaptor.capture(), any(), any(), any(), eq(tenantId));
    assertThat(sqlCaptor.getValue()).contains("type = 'REGULAR'");
    assertThat(sqlCaptor.getValue()).contains("status = 'TRIAL'");
    assertThat(sqlCaptor.getValue()).contains("trial_started_at IS NULL");
    assertThat(sqlCaptor.getValue()).contains("demo_mode = false");
  }

  @Test
  @DisplayName("active trials slide to last activity plus dormancy window")
  void shouldExtendEffectiveExpiryFromLastActivity() {
    Instant now = Instant.parse("2026-06-27T12:00:00Z");
    Instant started = now.minus(30, ChronoUnit.DAYS);
    Instant lastActivity = now.minus(1, ChronoUnit.DAYS);

    TrialLifecycleService.TrialDecision decision =
        service.evaluate(
            new TrialLifecycleService.TrialWindow(UUID.randomUUID(), started, lastActivity), now);

    assertThat(decision.status()).isEqualTo(TenantStatus.TRIAL);
    assertThat(decision.effectiveExpiry()).isEqualTo(lastActivity.plus(90, ChronoUnit.DAYS));
  }

  @Test
  @DisplayName("dormant trials expire instead of being deleted")
  void shouldExpireDormantTrial() {
    Instant now = Instant.parse("2026-06-27T12:00:00Z");
    Instant started = now.minus(120, ChronoUnit.DAYS);
    Instant lastActivity = now.minus(91, ChronoUnit.DAYS);

    TrialLifecycleService.TrialDecision decision =
        service.evaluate(
            new TrialLifecycleService.TrialWindow(UUID.randomUUID(), started, lastActivity), now);

    assertThat(decision.status()).isEqualTo(TenantStatus.EXPIRED);
    assertThat(decision.effectiveExpiry()).isEqualTo(lastActivity.plus(90, ChronoUnit.DAYS));
  }

  @Test
  @DisplayName("hard cap expires trials even with recent activity")
  void shouldExpireAtHardCap() {
    Instant now = Instant.parse("2026-06-27T12:00:00Z");
    Instant started = now.minus(19 * 31L, ChronoUnit.DAYS);
    Instant lastActivity = now.minus(1, ChronoUnit.DAYS);

    TrialLifecycleService.TrialDecision decision =
        service.evaluate(
            new TrialLifecycleService.TrialWindow(UUID.randomUUID(), started, lastActivity), now);

    assertThat(decision.status()).isEqualTo(TenantStatus.EXPIRED);
    assertThat(decision.effectiveExpiry())
        .isEqualTo(started.atOffset(java.time.ZoneOffset.UTC).plusMonths(18).toInstant());
  }

  @Test
  @DisplayName("activity touch is throttled to one write per tenant per UTC day")
  void shouldThrottleActivityTouch() {
    UUID tenantId = UUID.randomUUID();

    service.touchTenantActivity(tenantId);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(systemExecutor)
        .executeUpdate(
            sqlCaptor.capture(), any(Timestamp.class), eq(tenantId), any(Timestamp.class));
    assertThat(sqlCaptor.getValue()).contains("type = 'REGULAR'");
    assertThat(sqlCaptor.getValue()).contains("status = 'TRIAL'");
    assertThat(sqlCaptor.getValue()).contains("trial_started_at IS NOT NULL");
    assertThat(sqlCaptor.getValue()).contains("last_activity_at IS NULL OR last_activity_at < ?");
  }

  @Test
  @DisplayName("scheduled refresh updates only registered activated trial tenants")
  @SuppressWarnings("unchecked")
  void shouldRefreshRegisteredActivatedTrialWindows() {
    UUID tenantId = UUID.randomUUID();
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    TrialLifecycleService.TrialWindow dormantTrial =
        new TrialLifecycleService.TrialWindow(
            tenantId,
            Instant.now().minus(120, ChronoUnit.DAYS),
            Instant.now().minus(91, ChronoUnit.DAYS));
    when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(dormantTrial));
    when(jdbc.update(anyString(), eq("EXPIRED"), any(Timestamp.class), eq(tenantId))).thenReturn(1);
    when(systemExecutor.executeInTransaction(any(Function.class)))
        .thenAnswer(
            invocation -> {
              Function<JdbcTemplate, Integer> callback = invocation.getArgument(0);
              return callback.apply(jdbc);
            });

    service.refreshTrialWindows();

    ArgumentCaptor<String> selectSqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(selectSqlCaptor.capture(), any(RowMapper.class));
    assertThat(selectSqlCaptor.getValue()).contains("type = 'REGULAR'");
    assertThat(selectSqlCaptor.getValue()).contains("status = 'TRIAL'");
    assertThat(selectSqlCaptor.getValue()).contains("trial_started_at IS NOT NULL");
    verify(jdbc).update(anyString(), eq("EXPIRED"), any(Timestamp.class), eq(tenantId));
  }
}
