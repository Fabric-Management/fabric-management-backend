package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * FIBER-CATALOG-1 §6 (A09, A10) race coverage, replacing FIBER-SRC-1's tenant-ISO creation race:
 * approval never creates ISO rows, one request has exactly one outcome, and distinct requests for a
 * newly published code converge on the same shared ISO id.
 */
class FiberNewCodeApprovalRaceIT extends FiberSourceIntegrationSupport {

  @Test
  void concurrentApprovalsOfOneRequestProduceExactlyOneVariant() throws Exception {
    UUID tenantId = insertTenant("same-request-race");
    UUID requester = UUID.randomUUID();
    UUID reviewer = UUID.randomUUID();
    UUID requestId =
        insertPendingRequest(
            tenantId, requester, "CO", "Recycled Cotton", "NATURAL_PLANT", MaterialSource.RECYCLED);

    List<Throwable> outcomes =
        runTogether(
            () -> approve(tenantId, reviewer, requestId),
            () -> approve(tenantId, reviewer, requestId));

    assertThat(outcomes.stream().filter(Objects::isNull)).hasSize(1);
    assertThat(outcomes.stream().filter(Objects::nonNull))
        .singleElement()
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_REQUEST_INVALID_STATUS");
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ? "
                    + "AND material_source = 'RECYCLED'",
                tenantId))
        .isEqualTo(1L);
    assertThat(count("SELECT count(*) FROM production.prod_product WHERE tenant_id = ?", tenantId))
        .as("a rolled-back approval leaves no product behind")
        .isEqualTo(1L);
  }

  @Test
  void concurrentApprovalsForTwoSourcesOfAPublishedNewCodeShareOneIso() throws Exception {
    UUID tenantId = insertTenant("two-source-race");
    UUID requester = UUID.randomUUID();
    UUID reviewer = UUID.randomUUID();
    String isoCode = publishCatalogueCode(CATEGORY);
    UUID virginRequest =
        insertPendingRequest(
            tenantId, requester, isoCode, "Virgin New Fiber", CATEGORY, MaterialSource.VIRGIN);
    UUID recycledRequest =
        insertPendingRequest(
            tenantId, requester, isoCode, "Recycled New Fiber", CATEGORY, MaterialSource.RECYCLED);

    List<Throwable> failures =
        runTogether(
            () -> approve(tenantId, reviewer, virginRequest),
            () -> approve(tenantId, reviewer, recycledRequest));

    assertThat(failures).containsOnlyNulls();
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber_iso_code WHERE iso_code = ?", isoCode))
        .isEqualTo(1L);
    assertThat(
            count(
                "SELECT count(DISTINCT fiber_iso_code_id) FROM production.prod_fiber "
                    + "WHERE tenant_id = ?",
                tenantId))
        .isEqualTo(1L);
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ? "
                    + "AND fiber_iso_code_id = ?",
                tenantId,
                sharedIsoId(isoCode)))
        .isEqualTo(2L);
  }

  private Throwable approve(UUID tenantId, UUID reviewer, UUID requestId) {
    try {
      useTenant(tenantId, reviewer);
      fiberRequestService.approve(requestId, reviewer);
      return null;
    } catch (Throwable failure) {
      return failure;
    } finally {
      TenantContext.clear();
    }
  }

  private List<Throwable> runTogether(Callable<Throwable> first, Callable<Throwable> second)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<Throwable> firstFuture = executor.submit(gated(first, ready, start));
      Future<Throwable> secondFuture = executor.submit(gated(second, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return Arrays.asList(
          firstFuture.get(20, TimeUnit.SECONDS), secondFuture.get(20, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  private Callable<Throwable> gated(
      Callable<Throwable> task, CountDownLatch ready, CountDownLatch start) {
    return () -> {
      ready.countDown();
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("approval race did not start");
      }
      return task.call();
    };
  }
}
