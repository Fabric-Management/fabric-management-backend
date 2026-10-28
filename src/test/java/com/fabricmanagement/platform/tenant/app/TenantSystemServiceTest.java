package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.domain.TenantSettingsUnreadableException;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.dto.CreateTenantRequest;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class TenantSystemServiceTest {

  @Mock private DomainEventPublisher eventPublisher;
  @Mock private SystemTransactionExecutor systemExecutor;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private TenantUnderCreation tenantUnderCreation;
  @Mock private TenantSessionBinder tenantSessionBinder;

  private TenantSystemService tenantSystemService;

  @BeforeEach
  void setUp() {
    tenantSystemService =
        new TenantSystemService(
            eventPublisher,
            systemExecutor,
            new ObjectMapper(),
            tenantUnderCreation,
            tenantSessionBinder);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  // ── Settings: "never set" reads as defaults, "cannot be read" stops (2026-10-14) ──

  @Test
  void settingsNeverSet_readAsDefaults_fromBothReaders() {
    UUID tenantId = UUID.randomUUID();
    when(systemExecutor.executeQueryForObject(
            anyString(), ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class)))
        .thenReturn(null);

    assertThat(tenantSystemService.requireSettings(tenantId)).isEqualTo(TenantSettings.defaults());
    assertThat(tenantSystemService.getSettings(tenantId)).isEqualTo(TenantSettings.defaults());
  }

  @Test
  void settingsThatCannotBeParsed_throwFromRequire_andDefaultFromGet() {
    UUID tenantId = UUID.randomUUID();
    when(systemExecutor.executeQueryForObject(
            anyString(), ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class)))
        .thenReturn("{not json");

    assertThatThrownBy(() -> tenantSystemService.requireSettings(tenantId))
        .isInstanceOf(TenantSettingsUnreadableException.class);
    assertThat(tenantSystemService.getSettings(tenantId)).isEqualTo(TenantSettings.defaults());
  }

  @Test
  void settingsThatCannotBeRead_throwFromRequire() {
    UUID tenantId = UUID.randomUUID();
    when(systemExecutor.executeQueryForObject(
            anyString(), ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class)))
        .thenThrow(new IllegalStateException("connection lost"));

    assertThatThrownBy(() -> tenantSystemService.requireSettings(tenantId))
        .isInstanceOf(TenantSettingsUnreadableException.class)
        .hasMessageContaining(tenantId.toString());
  }

  @Test
  void shouldRetryTenantCreationWhenUidOrSlugIsTakenDuringInsert() {
    UUID tenantId = UUID.randomUUID();
    List<String> insertedUids = new ArrayList<>();
    List<UUID> insertedIds = new ArrayList<>();
    stubIdentityExistenceChecks();
    when(tenantUnderCreation.primaryJdbc()).thenReturn(jdbcTemplate);
    doAnswer(
            invocation -> {
              insertedIds.add(invocation.getArgument(1, UUID.class));
              insertedUids.add(invocation.getArgument(2, String.class));
              // ON CONFLICT DO NOTHING: the first attempt finds the uid taken and writes no row.
              return insertedUids.size() == 1 ? 0 : 1;
            })
        .when(jdbcTemplate)
        // sql + 11 values: id, uid, slug, name, type, status, settings, billing_email,
        // trial_ends_at, demo_mode, email_sandboxed (EMAIL-SANDBOX-1)
        .update(
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());
    when(systemExecutor.executeQuery(
            anyString(), ArgumentMatchers.<RowMapper<TenantDto>>any(), any()))
        .thenReturn(
            List.of(
                TenantDto.builder()
                    .id(tenantId)
                    .uid("ACME-002")
                    .slug("acme-textiles-2")
                    .status(TenantStatus.TRIAL)
                    .build()));

    TenantDto created =
        tenantSystemService.createTenant(
            CreateTenantRequest.builder()
                .name("Acme Textiles")
                .billingEmail("owner@example.com")
                .trialDays(90)
                .build());

    assertThat(created.getUid()).isEqualTo("ACME-002");
    assertThat(insertedUids).containsExactly("ACME-001", "ACME-002");
    // Each attempt binds the session to its own id first (the tenant table's policy checks the new
    // row), and only the row that was written is registered as "created by this transaction".
    assertThat(insertedIds).hasSize(2).doesNotHaveDuplicates();
    verify(tenantSessionBinder).bindToCurrentSession(insertedIds.get(0));
    verify(tenantSessionBinder).bindToCurrentSession(insertedIds.get(1));
    verify(tenantUnderCreation).register(insertedIds.get(1));
    verify(tenantUnderCreation, never()).register(insertedIds.get(0));
  }

  @Test
  void shouldGiveUpWhenNoFreeIdentityIsFoundInFiveAttempts() {
    when(tenantUnderCreation.primaryJdbc()).thenReturn(jdbcTemplate);
    when(jdbcTemplate.update(
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()))
        .thenReturn(0);

    assertThatThrownBy(
            () ->
                tenantSystemService.createTenant(
                    CreateTenantRequest.builder().name("Acme Textiles").trialDays(90).build()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Acme Textiles");

    verify(tenantSessionBinder, times(5)).bindToCurrentSession(any(UUID.class));
    verify(tenantUnderCreation, never()).register(any(UUID.class));
  }

  @Test
  void aTenantThisTransactionIsCreatingIsReadOnTheTransactionsConnection() {
    UUID tenantId = UUID.randomUUID();
    when(tenantUnderCreation.isUnderCreation(tenantId)).thenReturn(true);
    when(tenantUnderCreation.primaryJdbc()).thenReturn(jdbcTemplate);
    when(jdbcTemplate.query(
            anyString(), ArgumentMatchers.<RowMapper<TenantDto>>any(), any(Object[].class)))
        .thenReturn(List.of(TenantDto.builder().id(tenantId).status(TenantStatus.TRIAL).build()));

    assertThat(tenantSystemService.findById(tenantId)).isPresent();
    assertThat(tenantSystemService.findActiveById(tenantId)).isPresent();
    // Its row is uncommitted: the system connection would answer "not found".
    verifyNoInteractions(systemExecutor);
  }

  @Test
  void theSettingsOfATenantThisTransactionIsCreatingAreReadOnTheTransactionsConnection() {
    UUID tenantId = UUID.randomUUID();
    when(tenantUnderCreation.isUnderCreation(tenantId)).thenReturn(true);
    when(tenantUnderCreation.primaryJdbc()).thenReturn(jdbcTemplate);
    when(jdbcTemplate.query(
            anyString(), ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class)))
        .thenReturn(List.of("{}"));

    assertThat(tenantSystemService.getSettings(tenantId)).isNotNull();
    verifyNoInteractions(systemExecutor);
  }

  @Test
  void shouldEvictWritableAndDemoModeCachesOnTenantLifecycleChanges() throws Exception {
    assertTenantLifecycleEvictsTenantAccessCaches("activate", String.class);
    assertTenantLifecycleEvictsTenantAccessCaches("suspend", String.class);
    assertTenantLifecycleEvictsTenantAccessCaches("cancel", String.class);
  }

  private void stubIdentityExistenceChecks() {
    AtomicInteger acme001Checks = new AtomicInteger();
    AtomicInteger baseSlugChecks = new AtomicInteger();
    when(systemExecutor.executeQueryForObject(
            anyString(), ArgumentMatchers.<RowMapper<Integer>>any(), any()))
        .thenAnswer(
            invocation -> {
              Object lookupValue = invocation.getArguments()[invocation.getArguments().length - 1];
              return switch (String.valueOf(lookupValue)) {
                case "ACME-001" -> acme001Checks.getAndIncrement() == 0 ? 0 : 1;
                case "acme-textiles" -> baseSlugChecks.getAndIncrement() == 0 ? 0 : 1;
                case "ACME-002", "acme-textiles-2" -> 0;
                default -> 0;
              };
            });
  }

  private void assertTenantLifecycleEvictsTenantAccessCaches(
      String methodName, Class<?> secondArgumentType) throws NoSuchMethodException {
    Method method = TenantSystemService.class.getMethod(methodName, UUID.class, secondArgumentType);
    CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);

    assertThat(cacheEvict).isNotNull();
    assertThat(cacheEvict.value()).containsExactlyInAnyOrder("tenant-writable", "tenant-demomode");
    assertThat(cacheEvict.key()).isEqualTo("#tenantId.toString()");
  }
}
