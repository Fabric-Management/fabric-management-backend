package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FiberNewCodeApprovalRaceIT extends FiberSourceIntegrationSupport {

  @Test
  void concurrentApprovalsConvergeOnOneIsoAndCreateTwoSourceVariants() throws Exception {
    UUID tenantId = insertTenant("approval-race");
    UUID requester = UUID.randomUUID();
    UUID reviewer = UUID.randomUUID();
    insertCategory(tenantId, CATEGORY);
    String isoCode = randomCode();
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
            queryOne(
                "SELECT count(*) FROM production.prod_fiber_iso_code "
                    + "WHERE tenant_id = ? AND upper(iso_code) = ?",
                Long.class,
                tenantId,
                isoCode))
        .isEqualTo(1L);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_fiber f "
                    + "JOIN production.prod_fiber_iso_code i ON i.id = f.fiber_iso_code_id "
                    + "WHERE f.tenant_id = ? AND i.iso_code = ?",
                Long.class,
                tenantId,
                isoCode))
        .isEqualTo(2L);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.production_fiber_request "
                    + "WHERE tenant_id = ? AND iso_code = ? AND status = 'APPROVED'",
                Long.class,
                tenantId,
                isoCode))
        .isEqualTo(2L);
  }

  @Test
  void adoptedWinningIsoIsRevalidatedForFiberType() {
    UUID tenantId = insertTenant("approval-type-race");
    UUID requester = UUID.randomUUID();
    UUID reviewer = UUID.randomUUID();
    insertCategory(tenantId, CATEGORY);
    insertCategory(tenantId, "NATURAL_PLANT");
    String isoCode = randomCode();
    UUID winningRequest =
        insertPendingRequest(
            tenantId, requester, isoCode, "Winning Fiber", CATEGORY, MaterialSource.VIRGIN);
    UUID mismatchedRequest =
        insertPendingRequest(
            tenantId,
            requester,
            isoCode,
            "Mismatched Fiber",
            "NATURAL_PLANT",
            MaterialSource.RECYCLED);
    useTenant(tenantId, reviewer);
    fiberRequestService.approve(winningRequest, reviewer);

    assertThatThrownBy(() -> fiberRequestService.approve(mismatchedRequest, reviewer))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_REQUEST_FIBER_TYPE_MISMATCH");
  }

  @Test
  void jpaCreationAssignsDistinctTenantOwnedUidsInsteadOfDatabaseDefaults() {
    UUID tenantId = insertTenant("iso-lifecycle");
    UUID requester = UUID.randomUUID();
    UUID reviewer = UUID.randomUUID();
    insertCategory(tenantId, CATEGORY);
    String firstCode = randomCode();
    String secondCode = randomCode();
    UUID first =
        insertPendingRequest(
            tenantId, requester, firstCode, "First New Fiber", CATEGORY, MaterialSource.VIRGIN);
    UUID second =
        insertPendingRequest(
            tenantId, requester, secondCode, "Second New Fiber", CATEGORY, MaterialSource.RECYCLED);
    useTenant(tenantId, reviewer);

    fiberRequestService.approve(first, reviewer);
    fiberRequestService.approve(second, reviewer);

    List<String> uids =
        systemTransactions.executeQuery(
            "SELECT uid FROM production.prod_fiber_iso_code "
                + "WHERE tenant_id = ? AND iso_code IN (?, ?) ORDER BY iso_code",
            (resultSet, rowNumber) -> resultSet.getString(1),
            tenantId,
            firstCode,
            secondCode);
    assertThat(uids).hasSize(2).doesNotHaveDuplicates();
    assertThat(uids).allMatch(uid -> uid.matches("FSRC-[A-F0-9]{8}-FISO-[A-F0-9]{8}"));
    assertThat(uids).noneMatch(uid -> uid.equals("SYS-000-FISO-00000"));
  }

  private Throwable approve(UUID tenantId, UUID reviewer, UUID requestId) {
    try {
      useTenant(tenantId, reviewer);
      fiberRequestService.approve(requestId, reviewer);
      return null;
    } catch (Throwable failure) {
      return failure;
    } finally {
      com.fabricmanagement.common.infrastructure.persistence.TenantContext.clear();
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

  private String randomCode() {
    return "N" + UUID.randomUUID().toString().substring(0, 7).toUpperCase();
  }
}
