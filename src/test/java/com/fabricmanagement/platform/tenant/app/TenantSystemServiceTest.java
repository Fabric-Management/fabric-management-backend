package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.dto.CreateTenantRequest;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class TenantSystemServiceTest {

  @Mock private DomainEventPublisher eventPublisher;
  @Mock private SystemTransactionExecutor systemExecutor;
  @Mock private JdbcTemplate jdbcTemplate;

  private TenantSystemService tenantSystemService;

  @BeforeEach
  void setUp() {
    tenantSystemService =
        new TenantSystemService(eventPublisher, systemExecutor, new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldRetryTenantCreationWhenUidOrSlugCollidesDuringInsert() {
    UUID tenantId = UUID.randomUUID();
    List<String> insertedUids = new ArrayList<>();
    stubIdentityExistenceChecks();
    when(systemExecutor.executeInTransaction(ArgumentMatchers.<Function<JdbcTemplate, UUID>>any()))
        .thenAnswer(
            invocation -> {
              Function<JdbcTemplate, UUID> work = invocation.getArgument(0);
              return work.apply(jdbcTemplate);
            });
    doAnswer(
            invocation -> {
              insertedUids.add(invocation.getArgument(2, String.class));
              if (insertedUids.size() == 1) {
                throw new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint \"uk_tenant_uid\"");
              }
              return 1;
            })
        .when(jdbcTemplate)
        .update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
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
    verify(systemExecutor, times(2))
        .executeInTransaction(ArgumentMatchers.<Function<JdbcTemplate, UUID>>any());
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
