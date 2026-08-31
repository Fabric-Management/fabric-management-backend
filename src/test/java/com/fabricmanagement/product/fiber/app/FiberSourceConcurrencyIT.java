package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.event.FiberMaterialSourceDeclaredEvent;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class FiberSourceConcurrencyIT extends FiberSourceIntegrationSupport {

  @Autowired private FiberRepository fiberRepository;
  @Autowired private DomainEventPublisher eventPublisher;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void requestPartialIndexLeavesOnePendingAndSurfacesOneDuplicate409() throws Exception {
    UUID tenantId = insertTenant("request-race");
    UUID actorId = UUID.randomUUID();
    insertCategory(tenantId, CATEGORY);
    insertIso(tenantId, "PES", CATEGORY);

    List<Throwable> results =
        runTogether(() -> submit(tenantId, actorId), () -> submit(tenantId, actorId));

    assertThat(results.stream().filter(java.util.Objects::isNull)).hasSize(1);
    assertThat(results.stream().filter(java.util.Objects::nonNull).toList())
        .singleElement()
        .isInstanceOfSatisfying(
            FiberDomainException.class,
            failure -> {
              assertThat(failure.getErrorCode()).isEqualTo("FIBER_REQUEST_DUPLICATE_PENDING");
              assertThat(failure.getHttpStatus()).isEqualTo(409);
            });
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.production_fiber_request "
                    + "WHERE tenant_id = ? AND iso_code = 'PES' "
                    + "AND material_source = 'RECYCLED' AND status = 'PENDING'",
                Long.class,
                tenantId))
        .isEqualTo(1L);
  }

  @Test
  void pureFiberVariantPartialIndexAllowsOnlyOneConcurrentCreation() throws Exception {
    UUID tenantId = insertTenant("variant-race");
    UUID categoryId = insertCategory(tenantId, CATEGORY);
    UUID isoId = insertIso(tenantId, "PES", CATEGORY);

    List<Throwable> results =
        runTogether(
            () -> insertRacingPureFiber(tenantId, categoryId, isoId, "First"),
            () -> insertRacingPureFiber(tenantId, categoryId, isoId, "Second"));

    assertThat(results.stream().filter(java.util.Objects::isNull)).hasSize(1);
    assertThat(results.stream().filter(java.util.Objects::nonNull)).hasSize(1);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND fiber_iso_code_id = ? "
                    + "AND material_source = 'RECYCLED'",
                Long.class,
                tenantId,
                isoId))
        .isEqualTo(1L);
  }

  @Test
  void optimisticDeclarationRaceCommitsOneChangeAndOneActorCorrectAudit() throws Exception {
    UUID tenantId = insertTenant("declaration-race");
    UUID actorId = UUID.randomUUID();
    UUID categoryId = insertCategory(tenantId, CATEGORY);
    UUID isoId = insertIso(tenantId, "PES", CATEGORY);
    UUID fiberId = insertPureFiber(tenantId, categoryId, isoId, "Legacy PES", null);
    CountDownLatch loaded = new CountDownLatch(2);
    CountDownLatch mutate = new CountDownLatch(1);

    List<Throwable> results =
        runUngated(
            () -> declareInTransaction(tenantId, actorId, fiberId, loaded, mutate),
            () -> declareInTransaction(tenantId, actorId, fiberId, loaded, mutate),
            loaded,
            mutate);

    assertThat(results.stream().filter(java.util.Objects::isNull)).hasSize(1);
    Throwable loser = results.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow();
    assertThat(hasOptimisticLockCause(loser)).isTrue();
    assertThat(
            queryOne(
                "SELECT material_source FROM production.prod_fiber WHERE id = ?",
                String.class,
                fiberId))
        .isEqualTo("RECYCLED");
    assertThat(
            queryOne("SELECT version FROM production.prod_fiber WHERE id = ?", Long.class, fiberId))
        .isEqualTo(1L);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        queryOne(
                            "SELECT count(*) FROM common_audit.common_audit_log "
                                + "WHERE action = 'FIBER_MATERIAL_SOURCE_DECLARED' "
                                + "AND resource_id = ?",
                            Long.class,
                            fiberId.toString()))
                    .isEqualTo(1L));
    assertThat(
            queryOne(
                "SELECT user_id FROM common_audit.common_audit_log "
                    + "WHERE action = 'FIBER_MATERIAL_SOURCE_DECLARED' AND resource_id = ?",
                UUID.class,
                fiberId.toString()))
        .isEqualTo(actorId);
    assertThat(
            queryOne(
                "SELECT user_uid FROM common_audit.common_audit_log "
                    + "WHERE action = 'FIBER_MATERIAL_SOURCE_DECLARED' AND resource_id = ?",
                String.class,
                fiberId.toString()))
        .isNotEqualTo("SYSTEM");
  }

  private Throwable submit(UUID tenantId, UUID actorId) {
    try {
      useTenant(tenantId, actorId);
      fiberRequestService.submit(
          request("PES", "Racing Recycled PES", CATEGORY, MaterialSource.RECYCLED),
          tenantId,
          actorId);
      return null;
    } catch (Throwable failure) {
      return failure;
    } finally {
      TenantContext.clear();
    }
  }

  private Throwable insertRacingPureFiber(
      UUID tenantId, UUID categoryId, UUID isoId, String fiberName) {
    try {
      systemTransactions.executeInTransaction(
          jdbc -> {
            UUID productId = UUID.randomUUID();
            jdbc.update(
                "INSERT INTO production.prod_product "
                    + "(id, tenant_id, uid, product_type, unit, is_active) "
                    + "VALUES (?, ?, ?, 'FIBER', 'KG', TRUE)",
                productId,
                tenantId,
                uid("PROD"));
            jdbc.update(
                "INSERT INTO production.prod_fiber "
                    + "(id, tenant_id, uid, product_id, fiber_category_id, fiber_iso_code_id, "
                    + "fiber_name, composition, status, material_source, is_active) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, 'ACTIVE', 'RECYCLED', TRUE)",
                UUID.randomUUID(),
                tenantId,
                uid("FIBR"),
                productId,
                categoryId,
                isoId,
                fiberName);
            return null;
          });
      return null;
    } catch (Throwable failure) {
      return failure;
    }
  }

  private Throwable declareInTransaction(
      UUID tenantId, UUID actorId, UUID fiberId, CountDownLatch loaded, CountDownLatch mutate) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    try {
      useTenant(tenantId, actorId);
      transaction.executeWithoutResult(
          ignored -> {
            Fiber fiber = fiberRepository.findByTenantIdAndId(tenantId, fiberId).orElseThrow();
            loaded.countDown();
            awaitLatch(mutate);
            fiber.declareMaterialSource(MaterialSource.RECYCLED);
            fiberRepository.save(fiber);
            eventPublisher.publish(
                new FiberMaterialSourceDeclaredEvent(
                    tenantId, fiberId, null, MaterialSource.RECYCLED, actorId));
          });
      return null;
    } catch (Throwable failure) {
      return failure;
    } finally {
      TenantContext.clear();
    }
  }

  private List<Throwable> runTogether(Callable<Throwable> first, Callable<Throwable> second)
      throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    return run(
        () -> gated(first, ready, start),
        () -> gated(second, ready, start),
        () -> {
          assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
          start.countDown();
        });
  }

  private List<Throwable> runUngated(
      Callable<Throwable> first,
      Callable<Throwable> second,
      CountDownLatch loaded,
      CountDownLatch mutate)
      throws Exception {
    return run(
        first,
        second,
        () -> {
          assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
          mutate.countDown();
        });
  }

  private List<Throwable> run(
      Callable<Throwable> first, Callable<Throwable> second, ThrowingAction release)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Throwable> firstFuture = executor.submit(first);
      Future<Throwable> secondFuture = executor.submit(second);
      release.run();
      return Arrays.asList(
          firstFuture.get(20, TimeUnit.SECONDS), secondFuture.get(20, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  private Throwable gated(Callable<Throwable> action, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    awaitLatch(start);
    return action.call();
  }

  private void awaitLatch(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("concurrency coordination timed out");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("concurrency coordination was interrupted", exception);
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

  @FunctionalInterface
  private interface ThrowingAction {
    void run() throws Exception;
  }
}
