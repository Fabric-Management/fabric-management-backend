package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantTransactionalPurgeServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Instant NOW = Instant.parse("2026-06-27T12:00:00Z");

  @Mock private SystemTransactionExecutor systemExecutor;
  @Mock private CacheManager cacheManager;
  @Mock private Cache cache;
  @Mock private JdbcTemplate jdbc;

  private TenantTransactionalPurgeService service;

  @BeforeEach
  void setUp() {
    service =
        new TenantTransactionalPurgeService(
            systemExecutor, cacheManager, Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(service, "baseDays", 90);
  }

  @Test
  void shouldPurgeTransactionalTablesSeedUsersFlipDemoModeAndEvictCache() {
    stubSystemTransaction();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(TENANT_ID))).thenReturn(true);
    doReturn(1)
        .when(jdbc)
        .update(
            contains("UPDATE common_tenant.common_tenant"),
            any(Timestamp.class),
            any(Timestamp.class),
            any(Timestamp.class),
            eq(TENANT_ID));
    when(cacheManager.getCache("tenant-demomode")).thenReturn(cache);

    TenantTransactionalPurgeService.PurgeResult result = service.goReal(TENANT_ID);

    assertThat(result.tenantId()).isEqualTo(TENANT_ID);
    assertThat(result.trialStartedAt()).isEqualTo(NOW);
    assertThat(result.trialEndsAt()).isEqualTo(Instant.parse("2026-09-25T12:00:00Z"));
    assertThat(result.deletedRows())
        .containsKeys(
            "sales.quote",
            "procurement.purchase_order",
            "production.prod_product",
            "iwm.warehouse_location",
            "finance.finance_invoice",
            "flowboard.task",
            "common_approval.approval_request",
            "notification.notification_queue",
            "common_company.common_organization(external-partners)",
            "common_company.common_trading_partner+unreferenced_registry",
            "human.human_employee_number_sequence",
            "common_user.common_user(seed-demo-users)");
    verify(jdbc).update(contains("DELETE FROM sales.quote WHERE tenant_id = ?"), eq(TENANT_ID));
    verify(jdbc)
        .update(
            contains("DELETE FROM procurement.purchase_order WHERE tenant_id = ?"), eq(TENANT_ID));
    verify(jdbc)
        .update(contains("DELETE FROM production.prod_product WHERE tenant_id = ?"), eq(TENANT_ID));
    verify(jdbc)
        .update(
            contains(
                "DELETE FROM common_user.common_user WHERE tenant_id = ? AND demo_seed = true"),
            eq(TENANT_ID));
    verify(jdbc)
        .update(
            contains(
                "DELETE FROM common_company.common_organization\n"
                    + "WHERE tenant_id = ? AND organization_type = 'EXTERNAL_PARTNER'"),
            eq(TENANT_ID));
    verify(jdbc)
        .update(
            contains("DELETE FROM common_company.trading_partner_registry r"),
            eq(TENANT_ID),
            eq(TENANT_ID));
    verify(cache).evict(TENANT_ID.toString());
  }

  @Test
  void shouldRefuseNonDemoTenantBeforeDeletingAnything() {
    stubSystemTransaction();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(TENANT_ID))).thenReturn(false);

    assertThatThrownBy(() -> service.goReal(TENANT_ID))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("TENANT_ALREADY_REAL", 409);

    verify(jdbc, never())
        .update(contains("DELETE FROM sales.quote WHERE tenant_id = ?"), eq(TENANT_ID));
    verify(cacheManager, never()).getCache("tenant-demomode");
  }

  @Test
  void shouldPropagateFailureSoTransactionExecutorCanRollbackAndSkipCacheEviction() {
    RuntimeException failure = new RuntimeException("forced purge failure");
    stubSystemTransaction();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(TENANT_ID))).thenReturn(true);
    doThrow(failure)
        .when(jdbc)
        .update(contains("DELETE FROM finance.finance_payment WHERE tenant_id = ?"), eq(TENANT_ID));

    assertThatThrownBy(() -> service.goReal(TENANT_ID)).isSameAs(failure);
    verify(cacheManager, never()).getCache("tenant-demomode");
  }

  @Test
  void shouldNotDeleteIdentityConfigurationTablesWholesale() {
    stubSystemTransaction();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(TENANT_ID))).thenReturn(true);
    doReturn(1)
        .when(jdbc)
        .update(
            contains("UPDATE common_tenant.common_tenant"),
            any(Timestamp.class),
            any(Timestamp.class),
            any(Timestamp.class),
            eq(TENANT_ID));

    service.goReal(TENANT_ID);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sqlCaptor.capture(), eq(TENANT_ID));
    List<String> sql = sqlCaptor.getAllValues();
    assertThat(sql)
        .noneMatch(statement -> statement.contains("DELETE FROM common_tenant.common_tenant"))
        .noneMatch(
            statement ->
                statement.contains("DELETE FROM common_company.common_organization")
                    && !statement.contains("organization_type = 'EXTERNAL_PARTNER'"))
        .noneMatch(
            statement -> statement.contains("DELETE FROM common_company.common_subscription"))
        .noneMatch(statement -> statement.contains("DELETE FROM common_user.common_role"))
        .noneMatch(statement -> statement.contains("DELETE FROM common_user.permission_template"))
        .noneMatch(statement -> statement.contains("DELETE FROM common_company.common_department"))
        .noneMatch(statement -> statement.contains("DELETE FROM human.human_holiday_calendar"))
        .noneMatch(
            statement -> statement.contains("DELETE FROM human.human_hr_country_pack_mapping"))
        .noneMatch(statement -> statement.contains("DELETE FROM human.human_hr_policy_pack"))
        .noneMatch(statement -> statement.contains("DELETE FROM human.human_leave_type"));
  }

  @Test
  void shouldDeleteProductionReferenceTablesInFkSafeOrder() {
    stubSystemTransaction();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(TENANT_ID))).thenReturn(true);
    doReturn(1)
        .when(jdbc)
        .update(
            contains("UPDATE common_tenant.common_tenant"),
            any(Timestamp.class),
            any(Timestamp.class),
            any(Timestamp.class),
            eq(TENANT_ID));

    service.goReal(TENANT_ID);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sqlCaptor.capture(), eq(TENANT_ID));
    List<String> sql = sqlCaptor.getAllValues();
    assertThat(indexOf(sql, "DELETE FROM production.prod_product_attribute WHERE tenant_id = ?"))
        .isLessThan(indexOf(sql, "DELETE FROM production.prod_fiber WHERE tenant_id = ?"));
    assertThat(indexOf(sql, "DELETE FROM production.quality_grade WHERE tenant_id = ?"))
        .isLessThan(indexOf(sql, "DELETE FROM production.prod_product WHERE tenant_id = ?"));
    assertThat(indexOf(sql, "DELETE FROM production.prod_fiber_certification WHERE tenant_id = ?"))
        .isLessThan(indexOf(sql, "DELETE FROM production.prod_fiber WHERE tenant_id = ?"));
    assertThat(
            indexOf(sql, "DELETE FROM production.prod_fiber_quality_standard WHERE tenant_id = ?"))
        .isLessThan(indexOf(sql, "DELETE FROM production.prod_fiber WHERE tenant_id = ?"));
    assertThat(indexOf(sql, "DELETE FROM production.prod_fiber WHERE tenant_id = ?"))
        .isLessThan(indexOf(sql, "DELETE FROM production.prod_product WHERE tenant_id = ?"));
    assertThat(indexOf(sql, "DELETE FROM production.prod_fiber WHERE tenant_id = ?"))
        .isLessThan(indexOf(sql, "DELETE FROM production.prod_fiber_iso_code WHERE tenant_id = ?"));
    assertThat(indexOf(sql, "DELETE FROM production.prod_fiber WHERE tenant_id = ?"))
        .isLessThan(indexOf(sql, "DELETE FROM production.prod_fiber_category WHERE tenant_id = ?"));
  }

  @Test
  void shouldDeleteExternalPartnerOrganizationsAndOnlyUnreferencedRegistryRows() {
    stubSystemTransaction();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(TENANT_ID))).thenReturn(true);
    doReturn(1)
        .when(jdbc)
        .update(
            contains("UPDATE common_tenant.common_tenant"),
            any(Timestamp.class),
            any(Timestamp.class),
            any(Timestamp.class),
            eq(TENANT_ID));

    service.goReal(TENANT_ID);

    verify(jdbc)
        .update(
            contains(
                "DELETE FROM common_company.common_organization\n"
                    + "WHERE tenant_id = ? AND organization_type = 'EXTERNAL_PARTNER'"),
            eq(TENANT_ID));

    ArgumentCaptor<String> twoTenantSqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc, org.mockito.Mockito.atLeastOnce())
        .update(twoTenantSqlCaptor.capture(), eq(TENANT_ID), eq(TENANT_ID));

    String registryCleanup =
        findStatement(
            twoTenantSqlCaptor.getAllValues(),
            "DELETE FROM common_company.trading_partner_registry r");
    assertThat(registryCleanup)
        .contains("DELETE FROM common_company.common_trading_partner")
        .contains("WHERE tenant_id = ?")
        .contains("RETURNING registry_id")
        .contains("AND NOT EXISTS")
        .contains("remaining.registry_id = r.id")
        .contains("remaining.tenant_id <> ?");
  }

  private int indexOf(List<String> statements, String needle) {
    for (int i = 0; i < statements.size(); i++) {
      if (statements.get(i).contains(needle)) {
        return i;
      }
    }
    throw new AssertionError("SQL statement not found: " + needle);
  }

  private String findStatement(List<String> statements, String needle) {
    for (String statement : statements) {
      if (statement.contains(needle)) {
        return statement;
      }
    }
    throw new AssertionError("SQL statement not found: " + needle);
  }

  @SuppressWarnings("unchecked")
  private void stubSystemTransaction() {
    when(systemExecutor.executeInTransaction(any(Function.class)))
        .thenAnswer(
            invocation -> {
              Function<JdbcTemplate, Map<String, Integer>> work = invocation.getArgument(0);
              return work.apply(jdbc);
            });
  }
}
