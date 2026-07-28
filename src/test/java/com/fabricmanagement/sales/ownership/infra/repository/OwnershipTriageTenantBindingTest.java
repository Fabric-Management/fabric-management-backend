package com.fabricmanagement.sales.ownership.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.ownership.domain.OwnershipTriageCase;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OwnershipTriageTenantBindingTest {

  @Mock private NamedParameterJdbcTemplate jdbc;
  @Mock private EntityManager entityManager;

  private OwnershipTriageQueryRepository queryRepository;
  private OwnershipTriageCaseLogRepository caseLogRepository;

  @BeforeEach
  void setUp() {
    queryRepository = new OwnershipTriageQueryRepository(jdbc);
    caseLogRepository = new OwnershipTriageCaseLogRepository();
    ReflectionTestUtils.setField(caseLogRepository, "entityManager", entityManager);
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void schedulerStyleAmbientTenantCanBindAndQuery() {
    UUID tenantId = UUID.randomUUID();
    when(jdbc.queryForObject(startsWith("SELECT set_config"), anyMap(), eq(String.class)))
        .thenReturn(tenantId.toString());
    when(jdbc.query(
            anyString(),
            anyMap(),
            org.mockito.ArgumentMatchers.<RowMapper<OwnershipTriageCase>>any()))
        .thenReturn(List.of());

    List<OwnershipTriageCase> result =
        TenantContext.executeInTenantContext(tenantId, () -> queryRepository.findAll(tenantId));

    assertThat(result).isEmpty();
  }

  @Test
  void queryTenantMismatchFailsBeforeSetConfig() {
    UUID ambientTenantId = UUID.randomUUID();
    UUID requestedTenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(ambientTenantId);

    assertThatThrownBy(() -> queryRepository.findAll(requestedTenantId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Triage query tenant mismatch: ambient=%s requested=%s",
            ambientTenantId, requestedTenantId);
    verifyNoInteractions(jdbc);
  }

  @Test
  void caseLogTenantMismatchFailsBeforeSetConfig() {
    UUID ambientTenantId = UUID.randomUUID();
    UUID requestedTenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(ambientTenantId);

    assertThatThrownBy(
            () ->
                caseLogRepository.tryRecordNotificationRequested(
                    requestedTenantId,
                    UUID.randomUUID(),
                    Instant.parse("2026-07-28T12:00:00Z"),
                    Instant.parse("2026-07-28T13:00:00Z")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Triage case-log tenant mismatch: ambient=%s requested=%s",
            ambientTenantId, requestedTenantId);
    verifyNoInteractions(entityManager);
  }

  @Test
  void missingAmbientTenantRejectsBothRepositoriesBeforeSetConfig() {
    UUID requestedTenantId = UUID.randomUUID();

    assertThatThrownBy(() -> queryRepository.findAll(requestedTenantId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("TenantContext is not set. Cannot proceed without tenant.");
    assertThatThrownBy(
            () ->
                caseLogRepository.tryRecordNotificationRequested(
                    requestedTenantId,
                    UUID.randomUUID(),
                    Instant.parse("2026-07-28T12:00:00Z"),
                    Instant.parse("2026-07-28T13:00:00Z")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("TenantContext is not set. Cannot proceed without tenant.");
    verifyNoInteractions(jdbc, entityManager);
  }
}
