package com.fabricmanagement.production.masterdata.color.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerRef;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorPartnerRefRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ColorPartnerRefLockingIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ColorPartnerRefRepository repository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void childOnlyPrimarySwitchIncrementsTheForcedLockedRootVersion() {
    UUID tenantId = UUID.randomUUID();
    UUID colorId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();
    UUID refId = UUID.randomUUID();
    UUID oldPrimaryId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    insertFixture(tenantId, colorId, partnerId, refId, oldPrimaryId, targetId);
    TenantContext.setCurrentTenantId(tenantId);

    Long before =
        jdbcTemplate.queryForObject(
            "SELECT version FROM production.color_partner_ref WHERE id = ?", Long.class, refId);

    transactionTemplate.executeWithoutResult(
        ignored -> {
          ColorPartnerRef ref =
              repository.findForMutationByTenantIdAndId(tenantId, refId).orElseThrow();
          ref.preparePrimarySwitch(targetId);
          entityManager.flush();
          ref.completePrimarySwitch(targetId);
          repository.save(ref);
        });

    Long after =
        jdbcTemplate.queryForObject(
            "SELECT version FROM production.color_partner_ref WHERE id = ?", Long.class, refId);
    Boolean targetIsPrimary =
        jdbcTemplate.queryForObject(
            "SELECT is_primary FROM production.color_partner_code WHERE id = ?",
            Boolean.class,
            targetId);

    assertThat(after).isEqualTo(before + 1);
    assertThat(targetIsPrimary).isTrue();
  }

  @Test
  void staleConcurrentChildMutationLosesWithAnOptimisticLockFailure() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID colorId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();
    UUID refId = UUID.randomUUID();
    UUID oldPrimaryId = UUID.randomUUID();
    UUID aliasId = UUID.randomUUID();
    insertFixture(tenantId, colorId, partnerId, refId, oldPrimaryId, aliasId);

    CountDownLatch staleWriterLoaded = new CountDownLatch(1);
    CountDownLatch winningWriterCommitted = new CountDownLatch(1);
    ExecutorService writers = Executors.newFixedThreadPool(2);
    try {
      var first =
          writers.submit(
              () -> {
                Throwable failure =
                    winningCodeNameUpdate(
                        tenantId, refId, aliasId, "Writer one", staleWriterLoaded);
                winningWriterCommitted.countDown();
                return failure;
              });
      var second =
          writers.submit(
              () ->
                  staleCodeNameUpdate(
                      tenantId,
                      refId,
                      aliasId,
                      "Writer two",
                      staleWriterLoaded,
                      winningWriterCommitted));

      Throwable winningFailure = first.get(20, TimeUnit.SECONDS);
      Throwable staleFailure = second.get(20, TimeUnit.SECONDS);

      assertThat(winningFailure).isNull();
      assertThat(staleFailure).isNotNull();
      assertThat(hasOptimisticLockCause(staleFailure)).isTrue();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT version FROM production.color_partner_ref WHERE id = ?",
                  Long.class,
                  refId))
          .isEqualTo(1L);
    } finally {
      writers.shutdownNow();
    }
  }

  private Throwable winningCodeNameUpdate(
      UUID tenantId, UUID refId, UUID codeId, String name, CountDownLatch staleWriterLoaded) {
    TransactionTemplate writer = new TransactionTemplate(transactionManager);
    writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    try {
      TenantContext.setCurrentTenantId(tenantId);
      writer.executeWithoutResult(
          ignored -> {
            ColorPartnerRef ref =
                repository.findForMutationByTenantIdAndId(tenantId, refId).orElseThrow();
            await(staleWriterLoaded);
            ref.updateCodeName(codeId, name);
            repository.save(ref);
          });
      return null;
    } catch (Throwable failure) {
      return failure;
    } finally {
      TenantContext.clear();
    }
  }

  private Throwable staleCodeNameUpdate(
      UUID tenantId,
      UUID refId,
      UUID codeId,
      String name,
      CountDownLatch staleWriterLoaded,
      CountDownLatch winningWriterCommitted) {
    TransactionTemplate writer = new TransactionTemplate(transactionManager);
    writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    try {
      TenantContext.setCurrentTenantId(tenantId);
      writer.executeWithoutResult(
          ignored -> {
            ColorPartnerRef ref =
                repository.findForMutationByTenantIdAndId(tenantId, refId).orElseThrow();
            staleWriterLoaded.countDown();
            await(winningWriterCommitted);
            ref.updateCodeName(codeId, name);
            repository.save(ref);
          });
      return null;
    } catch (Throwable failure) {
      return failure;
    } finally {
      TenantContext.clear();
    }
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Concurrent writer coordination timed out");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Concurrent writer coordination was interrupted", exception);
    }
  }

  private boolean hasOptimisticLockCause(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof ObjectOptimisticLockingFailureException
          || current instanceof OptimisticLockException
          || current instanceof StaleObjectStateException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private void insertFixture(
      UUID tenantId, UUID colorId, UUID partnerId, UUID refId, UUID oldPrimaryId, UUID targetId) {
    jdbcTemplate.update(
        "INSERT INTO production.color "
            + "(id, tenant_id, code, name, color_type, color_family, standard_status, "
            + "is_active, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, 'Locking color', 'DYED', 'BLUE', 'DRAFT', true, now(), now(), 0)",
        colorId,
        tenantId,
        "LOCK-" + colorId.toString().substring(0, 8).toUpperCase());
    jdbcTemplate.update(
        "INSERT INTO production.color_partner_ref "
            + "(id, tenant_id, color_id, partner_id, role, is_active, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, 'SUPPLIER', true, now(), now(), 0)",
        refId,
        tenantId,
        colorId,
        partnerId);
    jdbcTemplate.update(
        "INSERT INTO production.color_partner_code "
            + "(id, tenant_id, color_partner_ref_id, partner_id, role, external_code, "
            + "external_code_key, is_primary, is_active, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, 'SUPPLIER', 'OLD', 'OLD', true, true, now(), now(), 0), "
            + "(?, ?, ?, ?, 'SUPPLIER', 'NEW', 'NEW', false, true, now(), now(), 0)",
        oldPrimaryId,
        tenantId,
        refId,
        partnerId,
        targetId,
        tenantId,
        refId,
        partnerId);
  }
}
