package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.platform.tenant.app.TenantTransactionalPurgeService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditRetentionJob.Totals;
import com.fabricmanagement.sales.salesorder.domain.OrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderEditBaseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

/**
 * Base trust, expiry and the retention cleanup of the safe edit (CEDIT-02 §11, CEDIT-03 §6) on real
 * PostgreSQL: expired and cleaned bases on save, the clock boundaries, and what {@link
 * SalesOrderEditRetentionJob} may and may not delete — per tenant, concurrently, around locked rows
 * — plus the demo purge of the technical edit records.
 *
 * <p>{@code purgeAll} cleans every active tenant of the shared container; each assertion therefore
 * reads this test's own rows, never the run's totals.
 */
class SalesOrderEditRetentionIT extends SalesOrderEditItSupport {

  private static final long BOUND_SECONDS = 20;

  @Autowired private SalesOrderEditRetentionJob retention;
  @Autowired private SalesOrderEditProperties properties;
  @Autowired private OrderEditBaseRepository baseRepository;
  @Autowired private TenantTransactionalPurgeService purge;

  // ── S11.3 / S11.4 / S11.5: expired and cleaned bases on save ──────────────

  @Test
  @DisplayName(
      "S11.3: an expired base with notes changed meanwhile is 409 EDIT_BASE_EXPIRED,"
          + " paymentTerms REVIEW_REQUIRED, currentBase of origin EXPIRED; nothing is written")
  void expiredBaseIsReviewed() {
    UUID operationId = UUID.randomUUID();
    ExpiredReview review = expiredReview(operationId);

    assertThat(review.failure()).isInstanceOf(SalesOrderEditConflictException.class);
    assertThat(failureCode(review.failure())).isEqualTo("EDIT_BASE_EXPIRED");
    assertThat(((DomainException) review.failure()).getHttpStatus()).isEqualTo(409);
    JsonNode problem = review.problem();
    assertThat(problem.path("code").asText()).isEqualTo("EDIT_BASE_EXPIRED");
    assertThat(problem.path("operationId").asText()).isEqualTo(operationId.toString());
    assertThat(problem.path("baseId").asText()).isEqualTo(review.stale().baseId().toString());
    assertThat(problem.path("conflicts")).hasSize(1);
    JsonNode conflict = problem.path("conflicts").get(0);
    assertThat(conflict.path("key").asText()).isEqualTo("paymentTerms");
    assertThat(conflict.path("reason").asText()).isEqualTo("REVIEW_REQUIRED");
    assertThat(conflict.path("base").asText()).isEqualTo("30 days");
    assertThat(conflict.path("current").asText()).isEqualTo("30 days");
    assertThat(conflict.path("mine").asText()).isEqualTo("60 days");
    assertThat(conflict.path("choices").toString()).contains("USE_MINE");

    UUID currentBase = review.currentBaseId();
    assertThat(baseColumn(currentBase, "origin")).isEqualTo("EXPIRED");
    assertThat(baseColumn(currentBase, "parent_base_id"))
        .isEqualTo(review.stale().baseId().toString());
    assertThat(problem.path("currentBase").path("orderVersion").asLong())
        .isEqualTo(review.versionAfterA());
    assertThat(storedExpiresAt(currentBase)).isAfter(clock.instant());

    assertThat(orderText("payment_terms")).isEqualTo("30 days");
    assertThat(orderText("notes")).isEqualTo("Urgent");
    assertThat(orderVersion()).isEqualTo(review.versionAfterA());
    assertThat(receipts("CONFLICT")).isEqualTo(1);
    assertThat(historyRows()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "S11.4: saving against the EXPIRED currentBase with paymentTerms USE_MINE is APPLIED and"
          + " keeps A's notes")
  void expiredReviewIsResolvedWithUseMine() {
    ExpiredReview review = expiredReview(UUID.randomUUID());

    SalesOrderEditResult result =
        saved(
            actorB,
            withResolutions(
                body(UUID.randomUUID(), review.currentBaseId(), "paymentTerms", set("60 days")),
                List.of(resolution("paymentTerms", null, "USE_MINE"))));

    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(result.resultVersion()).isEqualTo(review.versionAfterA() + 1);
    assertThat(orderText("payment_terms")).isEqualTo("60 days");
    assertThat(orderText("notes")).isEqualTo("Urgent");
    assertThat(orderVersion()).isEqualTo(result.resultVersion());
    assertThat(history())
        .filteredOn(row -> "paymentTerms".equals(row.get("edit_key")))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("resolution")).isEqualTo("USE_MINE");
              assertThat(row.get("old_value")).isEqualTo("\"30 days\"");
              assertThat(row.get("new_value")).isEqualTo("\"60 days\"");
              assertThat(row.get("actor_id")).isEqualTo(actorB.id());
            });
  }

  @Test
  @DisplayName("S11.5: a save against a base the cleanup removed is 409 EDIT_BASE_UNKNOWN")
  void cleanedBaseIsUnknown() {
    SalesOrderEditBase base = open(actorB);
    long version = orderVersion();
    ageBase(base.baseId(), pastRetention());
    retention.purgeAll(clock.instant());
    assertThat(baseExists(base.baseId())).isFalse();

    Object result =
        save(actorB, body(UUID.randomUUID(), base.baseId(), "paymentTerms", set("60 days")));

    assertThat(failureCode(result)).isEqualTo("EDIT_BASE_UNKNOWN");
    assertThat(((DomainException) result).getHttpStatus()).isEqualTo(409);
    assertThat(receipts()).isZero();
    assertThat(orderText("payment_terms")).isEqualTo("30 days");
    assertThat(orderVersion()).isEqualTo(version);
  }

  // ── Clock boundaries ──────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "S11.3: a base is valid before expiresAt and expired from expiresAt on; using it never"
          + " extends it")
  void expiryFollowsTheClock() {
    Duration ttl = properties.getBaseTtl();
    SalesOrderEditBase first = open(actorB);
    assertThat(first.expiresAt()).isEqualTo(first.capturedAt().plus(ttl));

    // The boundary itself, on the stored base: at expiresAt expired, one second before not.
    OrderEditBase stored =
        (OrderEditBase) as(actorB, () -> baseRepository.findById(first.baseId()).orElseThrow());
    assertThat(stored.isExpiredAt(stored.getExpiresAt())).isTrue();
    assertThat(stored.isExpiredAt(stored.getExpiresAt().minusSeconds(1))).isFalse();
    assertThat(stored.isExpiredAt(stored.getExpiresAt().plusNanos(1000))).isTrue();

    // Through a save: shortly before expiry the base still merges normally.
    clock.advance(ttl.minusMinutes(1));
    SalesOrderEditResult beforeExpiry =
        saved(actorB, body(UUID.randomUUID(), first.baseId(), "notes", set("Urgent")));
    assertThat(beforeExpiry.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(storedExpiresAt(first.baseId())).isEqualTo(stored.getExpiresAt());

    // The next base lives its own ttl from its own capture; the clock reaching it expires it.
    SalesOrderEditBase next = beforeExpiry.nextBase();
    assertThat(next.expiresAt()).isEqualTo(next.capturedAt().plus(ttl));
    clock.advance(ttl);
    Object atExpiry =
        save(actorB, body(UUID.randomUUID(), next.baseId(), "paymentTerms", set("60 days")));

    assertThat(failureCode(atExpiry)).isEqualTo("EDIT_BASE_EXPIRED");
    assertThat(orderText("payment_terms")).isEqualTo("30 days");
    assertThat(orderVersion()).isEqualTo(beforeExpiry.resultVersion());
  }

  @Test
  @DisplayName(
      "TIME-PRECISION-1: an opened and a derived base answer the capturedAt and expiresAt their"
          + " rows keep, and expire exactly at the answered expiresAt")
  void answeredBaseTimesAreTheStoredOnes() {
    SalesOrderEditBase opened = open(actorB);
    assertThat(opened.capturedAt()).isEqualTo(storedInstant(opened.baseId(), "captured_at"));
    assertThat(opened.expiresAt()).isEqualTo(storedExpiresAt(opened.baseId()));

    // A save derives its next base in memory and answers it before any read of the row.
    SalesOrderEditBase derived =
        saved(actorB, body(UUID.randomUUID(), opened.baseId(), "notes", set("Urgent"))).nextBase();
    assertThat(derived.capturedAt()).isEqualTo(storedInstant(derived.baseId(), "captured_at"));
    assertThat(derived.expiresAt()).isEqualTo(storedExpiresAt(derived.baseId()));

    // The expiry the client was told is the stored boundary, to the microsecond.
    OrderEditBase stored =
        (OrderEditBase) as(actorB, () -> baseRepository.findById(derived.baseId()).orElseThrow());
    assertThat(stored.isExpiredAt(derived.expiresAt())).isTrue();
    assertThat(stored.isExpiredAt(derived.expiresAt().minusNanos(1_000))).isFalse();
  }

  // ── Cleanup: what goes and what stays ─────────────────────────────────────

  @Test
  @DisplayName(
      "§6: the cleanup deletes a base only once it is older than expiresAt +"
          + " expired-base-retention, and never the order or its lines")
  void baseIsCleanedOnlyAfterItsReviewWindow() {
    SalesOrderEditBase base = open(actorB);
    Instant expiresAt = storedExpiresAt(base.baseId());
    Instant windowEnd = expiresAt.plus(properties.getExpiredBaseRetention());

    retention.purgeAll(expiresAt);
    assertThat(baseExists(base.baseId())).isTrue();
    retention.purgeAll(windowEnd);
    assertThat(baseExists(base.baseId())).isTrue();
    retention.purgeAll(windowEnd.plusMillis(1));
    assertThat(baseExists(base.baseId())).isFalse();

    assertThat(activeLines()).isEqualTo(2);
    assertThat(orderText("payment_terms")).isEqualTo("30 days");
  }

  @Test
  @DisplayName(
      "§6: the cleanup never deletes the parent of a base that is still valid; the parent goes"
          + " once its child is no longer valid")
  void parentOfValidChildIsKept() {
    SalesOrderEditBase parent = open(actorB);
    clock.advance(pastRetention());
    UUID operationId = UUID.randomUUID();
    Object expired = save(actorB, body(operationId, parent.baseId(), "notes", set("Urgent")));
    assertThat(failureCode(expired)).isEqualTo("EDIT_BASE_EXPIRED");
    UUID child = currentBaseOf(expired);
    assertThat(baseColumn(child, "parent_base_id")).isEqualTo(parent.baseId().toString());

    retention.purgeAll(clock.instant());
    assertThat(baseExists(parent.baseId())).as("parent of a valid child").isTrue();
    assertThat(baseExists(child)).isTrue();
    assertThat(receiptExists(tenantId, operationId)).isTrue();

    Instant childExpired = storedExpiresAt(child).plusMillis(1);
    retention.purgeAll(childExpired);
    assertThat(baseExists(parent.baseId())).isFalse();
    assertThat(baseExists(child)).as("expired child within its review window").isTrue();
    assertThat(receiptExists(tenantId, operationId)).as("named by the kept child").isTrue();
  }

  @Test
  @DisplayName(
      "§6: an old receipt is deleted only when no kept base names it; a living CONFLICT base"
          + " keeps its receipt and its repeat still answers the same conflict")
  void receiptIsKeptWhileABaseNamesIt() {
    SalesOrderEditBase baseA = open(actorA);
    SalesOrderEditBase baseB = open(actorB);
    UUID operationA = UUID.randomUUID();
    UUID operationB = UUID.randomUUID();
    SalesOrderEditResult byA =
        saved(actorA, body(operationA, baseA.baseId(), "paymentTerms", set("45 days")));
    Map<String, Object> requestB = body(operationB, baseB.baseId(), "paymentTerms", set("60 days"));
    JsonNode conflict = conflicted(actorB, requestB);
    UUID conflictBase = UUID.fromString(conflict.path("currentBase").path("baseId").asText());

    Duration old = properties.getOperationRetention().plusDays(1);
    ageReceipt(operationA, old);
    ageReceipt(operationB, old);
    retention.purgeAll(clock.instant());
    assertThat(receiptExists(tenantId, operationA)).as("named by A's next base").isTrue();
    assertThat(receiptExists(tenantId, operationB)).as("named by the conflict base").isTrue();

    ageBase(byA.nextBase().baseId(), pastRetention());
    retention.purgeAll(clock.instant());
    assertThat(baseExists(byA.nextBase().baseId())).isFalse();
    assertThat(receiptExists(tenantId, operationA)).as("no base names it any more").isFalse();
    assertThat(receiptExists(tenantId, operationB)).isTrue();
    assertThat(baseExists(conflictBase)).isTrue();

    JsonNode repeated = conflicted(actorB, requestB);
    assertThat(repeated.path("currentBase").path("baseId").asText())
        .isEqualTo(conflictBase.toString());
    assertThat(receipts("CONFLICT")).isEqualTo(1);
    assertThat(orderText("payment_terms")).isEqualTo("45 days");
  }

  @Test
  @DisplayName(
      "§6: a young receipt outlives its cleaned bases and still replays; the field history is"
          + " never cleaned and keeps its receipt correlation after the receipt is gone; a repeat"
          + " after cleanup is EDIT_BASE_UNKNOWN, not applied again")
  void historyOutlivesItsReceipt() {
    SalesOrderEditBase base = open(actorB);
    UUID operationId = UUID.randomUUID();
    Map<String, Object> request = body(operationId, base.baseId(), "notes", set("Urgent"));
    SalesOrderEditResult result = saved(actorB, request);
    UUID receiptId = receiptIdOf(tenantId, operationId);
    List<Map<String, Object>> before = history();
    assertThat(before).hasSize(1);

    // Bases past their window go first; the receipt is younger than its retention and stays,
    // so the same request still replays although every base it named is gone.
    retention.purgeAll(
        clock
            .instant()
            .plus(properties.getBaseTtl())
            .plus(properties.getExpiredBaseRetention())
            .plus(Duration.ofDays(1)));
    assertThat(baseExists(base.baseId())).isFalse();
    assertThat(baseExists(result.nextBase().baseId())).isFalse();
    assertThat(receiptExists(tenantId, operationId)).as("younger than its retention").isTrue();
    SalesOrderEditResult replayed = saved(actorB, request);
    assertThat(replayed.replayed()).isTrue();
    assertThat(replayed.resultVersion()).isEqualTo(result.resultVersion());
    assertThat(history()).isEqualTo(before);

    retention.purgeAll(clock.instant().plus(everythingEligible()));

    assertThat(basesIn(tenantId)).isZero();
    assertThat(receiptsIn(tenantId)).isZero();
    assertThat(history()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.order_field_change WHERE sales_order_id = ?"
                    + " AND operation_receipt_id = ? AND operation_id = ?",
                Integer.class,
                orderId,
                receiptId,
                operationId))
        .isEqualTo(1);
    assertThat(orderText("notes")).isEqualTo("Urgent");
    assertThat(orderVersion()).isEqualTo(result.resultVersion());

    Object repeated = save(actorB, request);
    assertThat(failureCode(repeated)).isEqualTo("EDIT_BASE_UNKNOWN");
    assertThat(history()).isEqualTo(before);
    assertThat(orderVersion()).isEqualTo(result.resultVersion());
    assertThat(receipts()).isZero();
  }

  // ── Cleanup: tenants, concurrency and locks ───────────────────────────────

  @Test
  @DisplayName(
      "§6: the cleanup is tenant-scoped: one tenant's run leaves another tenant's rows, and a run"
          + " over all tenants removes each tenant's eligible rows only")
  void cleanupIsTenantScoped() {
    OtherTenant other = anotherTenant();
    SalesOrderEditBase ownStale = open(actorB);
    SalesOrderEditBase ownFresh = open(actorA);
    SalesOrderEditBase otherStale = openOn(other.actorB(), other.orderId());
    SalesOrderEditBase otherFresh = openOn(other.actorA(), other.orderId());
    ageBase(ownStale.baseId(), pastRetention());
    ageBase(otherStale.baseId(), pastRetention());
    Instant now = clock.instant();

    Totals ownRun = job().purgeTenant(tenantId, now);

    assertThat(ownRun).isEqualTo(new Totals(1, 1, 0));
    assertThat(baseExists(ownStale.baseId())).isFalse();
    assertThat(baseExists(ownFresh.baseId())).isTrue();
    assertThat(baseExists(otherStale.baseId())).as("another tenant's eligible base").isTrue();
    assertThat(baseExists(otherFresh.baseId())).isTrue();

    retention.purgeAll(now);

    assertThat(baseExists(otherStale.baseId())).isFalse();
    assertThat(baseExists(otherFresh.baseId())).isTrue();
    assertThat(baseExists(ownFresh.baseId())).isTrue();
    assertThat(basesIn(tenantId)).isEqualTo(1);
    assertThat(basesIn(other.tenantId())).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "§6: two cleanups running at once delete each row once, raise no foreign-key error and"
          + " leave the history; a further run finds nothing")
  void concurrentCleanupsAreIdempotent() throws Exception {
    SalesOrderEditBase baseA = open(actorA);
    SalesOrderEditBase baseB = open(actorB);
    SalesOrderEditResult first =
        saved(actorB, body(UUID.randomUUID(), baseB.baseId(), "notes", set("Urgent")));
    conflicted(actorA, body(UUID.randomUUID(), baseA.baseId(), "notes", set("Later")));
    saved(
        actorB, body(UUID.randomUUID(), first.nextBase().baseId(), "paymentTerms", set("60 days")));
    assertThat(basesIn(tenantId)).isEqualTo(5);
    assertThat(receiptsIn(tenantId)).isEqualTo(3);
    int history = historyRows();
    Instant now = clock.instant().plus(everythingEligible());

    int batchSize = properties.getCleanupBatchSize();
    // One row per transaction, so the two runs interleave as much as they can.
    properties.setCleanupBatchSize(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Callable<Totals> run =
          () -> {
            await(start);
            return retention.purgeAll(now);
          };
      Future<Totals> one = workers.submit(run);
      Future<Totals> two = workers.submit(run);
      start.countDown();
      join(one);
      join(two);
    } finally {
      properties.setCleanupBatchSize(batchSize);
      workers.shutdownNow();
    }

    assertThat(basesIn(tenantId)).isZero();
    assertThat(receiptsIn(tenantId)).isZero();
    assertThat(historyRows()).isEqualTo(history);
    assertThat(job().purgeTenant(tenantId, now)).isEqualTo(new Totals(1, 0, 0));
    assertThat(orderText("notes")).isEqualTo("Urgent");
    assertThat(orderText("payment_terms")).isEqualTo("60 days");
  }

  @Test
  @DisplayName(
      "§6: a base share-locked by an open save is skipped by the cleanup and deleted after the"
          + " lock is released")
  void shareLockedBaseIsSkipped() throws Exception {
    SalesOrderEditBase base = open(actorB);
    ageBase(base.baseId(), pastRetention());

    try (Connection holder = ownerConnection()) {
      holder.setAutoCommit(false);
      try {
        shareLock(
            holder,
            "SELECT id FROM sales_ord.order_edit_base WHERE id = ? FOR SHARE",
            base.baseId());
        bounded(() -> retention.purgeAll(clock.instant()));
        assertThat(baseExists(base.baseId())).isTrue();
      } finally {
        holder.rollback();
      }
    }

    retention.purgeAll(clock.instant());
    assertThat(baseExists(base.baseId())).isFalse();
  }

  @Test
  @DisplayName(
      "§6: a receipt share-locked by an open repeat is skipped by the cleanup and deleted after"
          + " the lock is released")
  void shareLockedReceiptIsSkipped() throws Exception {
    SalesOrderEditBase base = open(actorB);
    UUID operationId = UUID.randomUUID();
    SalesOrderEditResult result =
        saved(actorB, body(operationId, base.baseId(), "notes", set("Urgent")));
    ageBase(base.baseId(), pastRetention());
    ageBase(result.nextBase().baseId(), pastRetention());
    ageReceipt(operationId, properties.getOperationRetention().plusDays(1));
    UUID receiptId = receiptIdOf(tenantId, operationId);

    try (Connection holder = ownerConnection()) {
      holder.setAutoCommit(false);
      try {
        shareLock(
            holder,
            "SELECT id FROM sales_ord.order_edit_operation WHERE id = ? FOR SHARE",
            receiptId);
        bounded(() -> retention.purgeAll(clock.instant()));
        assertThat(basesIn(tenantId)).isZero();
        assertThat(receiptExists(tenantId, operationId)).isTrue();
      } finally {
        holder.rollback();
      }
    }

    retention.purgeAll(clock.instant());
    assertThat(receiptExists(tenantId, operationId)).isFalse();
    assertThat(historyRows()).isEqualTo(1);
  }

  // ── Demo purge ────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "§6: the demo purge removes the tenant's history, bases and receipts with its orders and"
          + " leaves another tenant's edit records")
  void demoPurgeRemovesEditRecords() {
    OtherTenant other = anotherTenant();
    SalesOrderEditBase otherBase = openOn(other.actorB(), other.orderId());
    Object otherSave =
        saveOn(
            other.actorB(),
            other.orderId(),
            body(UUID.randomUUID(), otherBase.baseId(), "notes", set("Urgent")));
    assertThat(otherSave).isInstanceOf(SalesOrderEditResult.class);

    SalesOrderEditBase baseA = open(actorA);
    SalesOrderEditBase baseB = open(actorB);
    saved(
        actorA,
        withLines(
            body(UUID.randomUUID(), baseA.baseId(), "paymentTerms", set("45 days")),
            List.of(
                add(UUID.randomUUID(), UUID.randomUUID(), "quantity", set(quantity("300", "M"))))));
    conflicted(actorB, body(UUID.randomUUID(), baseB.baseId(), "paymentTerms", set("60 days")));
    int history = historyIn(tenantId);
    int bases = basesIn(tenantId);
    int receipts = receiptsIn(tenantId);
    assertThat(history).isEqualTo(2);
    assertThat(bases).isEqualTo(4);
    assertThat(receipts).isEqualTo(2);
    int otherHistory = historyIn(other.tenantId());
    int otherBases = basesIn(other.tenantId());
    int otherReceipts = receiptsIn(other.tenantId());

    jdbc.update("UPDATE common_tenant.common_tenant SET demo_mode = true WHERE id = ?", tenantId);
    Map<String, Integer> deleted = purge.purgeDemoData(tenantId).deletedRows();

    assertThat(deleted)
        .containsEntry("sales_ord.order_field_change", history)
        .containsEntry("sales_ord.order_edit_base", bases)
        .containsEntry("sales_ord.order_edit_operation", receipts);
    assertThat(historyIn(tenantId)).isZero();
    assertThat(basesIn(tenantId)).isZero();
    assertThat(receiptsIn(tenantId)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.sales_order WHERE id = ?", Integer.class, orderId))
        .isZero();
    assertThat(historyIn(other.tenantId())).isEqualTo(otherHistory);
    assertThat(basesIn(other.tenantId())).isEqualTo(otherBases);
    assertThat(receiptsIn(other.tenantId())).isEqualTo(otherReceipts);
  }

  // ── scenario builders ─────────────────────────────────────────────────────

  /** What S11.3 leaves: B's stale base, the 409 it got, and the order version after A's save. */
  private record ExpiredReview(
      SalesOrderEditBase stale, Object failure, JsonNode problem, long versionAfterA) {
    UUID currentBaseId() {
      return UUID.fromString(problem.path("currentBase").path("baseId").asText());
    }
  }

  /**
   * S11.3: B's base B0 is taken, A then sets notes to "Urgent", B0 passes its ttl, and B saves
   * {@code paymentTerms SET("60 days")} against it.
   */
  private ExpiredReview expiredReview(UUID operationId) {
    SalesOrderEditBase stale = open(actorB);
    SalesOrderEditBase baseA = open(actorA);
    saved(actorA, body(UUID.randomUUID(), baseA.baseId(), "notes", set("Urgent")));
    long versionAfterA = orderVersion();
    ageBase(stale.baseId(), properties.getBaseTtl());
    Object failure =
        save(actorB, body(operationId, stale.baseId(), "paymentTerms", set("60 days")));
    JsonNode problem =
        failure instanceof SalesOrderEditConflictException conflict ? conflict.body() : null;
    if (problem == null) {
      throw new AssertionError("Expected EDIT_BASE_EXPIRED but got " + failure);
    }
    return new ExpiredReview(stale, failure, problem, versionAfterA);
  }

  /** Another tenant from the shared fixture; this test's own tenant and actors stay current. */
  private record OtherTenant(UUID tenantId, Actor actorA, Actor actorB, UUID orderId) {}

  private OtherTenant anotherTenant() {
    UUID ownTenant = tenantId;
    Actor ownA = actorA;
    Actor ownB = actorB;
    Actor ownC = actorC;
    UUID ownPartner = partnerId;
    UUID ownOrder = orderId;
    UUID ownL1 = l1;
    UUID ownL2 = l2;
    UUID ownD1 = d1;
    UUID ownP1 = p1;
    UUID ownP2 = p2;
    createCommonStart();
    OtherTenant other = new OtherTenant(tenantId, actorA, actorB, orderId);
    tenantId = ownTenant;
    actorA = ownA;
    actorB = ownB;
    actorC = ownC;
    partnerId = ownPartner;
    orderId = ownOrder;
    l1 = ownL1;
    l2 = ownL2;
    d1 = ownD1;
    p1 = ownP1;
    p2 = ownP2;
    List.of(ownA, ownB, ownC).forEach(actor -> scopes.put(actor.id(), DataScope.GLOBAL));
    return other;
  }

  private SalesOrderEditBase openOn(Actor actor, UUID order) {
    Object result = as(actor, () -> edits.openBase(order, actor.id(), actor.authentication()));
    if (result instanceof RuntimeException failure) {
      throw failure;
    }
    return (SalesOrderEditBase) result;
  }

  private Object saveOn(Actor actor, UUID order, Map<String, Object> body) {
    var request = request(body);
    String path = PATH.formatted(order);
    return as(actor, () -> edits.save(order, request, actor.id(), actor.authentication(), path));
  }

  private static UUID currentBaseOf(Object failure) {
    if (failure instanceof SalesOrderEditConflictException conflict) {
      return UUID.fromString(conflict.body().path("currentBase").path("baseId").asText());
    }
    throw new AssertionError("Expected a recorded conflict but got " + failure);
  }

  /** The job itself, for the one-tenant run the scheduler wraps. */
  private SalesOrderEditRetentionJob job() {
    return AopTestUtils.getUltimateTargetObject(retention);
  }

  /** How far a base is moved back so that its review window has passed. */
  private Duration pastRetention() {
    return properties.getBaseTtl().plus(properties.getExpiredBaseRetention()).plusHours(1);
  }

  /** How far ahead every base and receipt written now is eligible for cleanup. */
  private Duration everythingEligible() {
    return properties
        .getOperationRetention()
        .plus(properties.getBaseTtl())
        .plus(properties.getExpiredBaseRetention())
        .plusDays(1);
  }

  // ── database reads and changes ────────────────────────────────────────────

  private Instant storedExpiresAt(UUID baseId) {
    return jdbc.queryForObject(
            "SELECT expires_at FROM sales_ord.order_edit_base WHERE id = ?",
            Timestamp.class,
            baseId)
        .toInstant();
  }

  private Instant storedInstant(UUID baseId, String column) {
    return jdbc.queryForObject(
            "SELECT " + column + " FROM sales_ord.order_edit_base WHERE id = ?",
            Timestamp.class,
            baseId)
        .toInstant();
  }

  private String baseColumn(UUID baseId, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + "::text FROM sales_ord.order_edit_base WHERE id = ?",
        String.class,
        baseId);
  }

  private boolean baseExists(UUID baseId) {
    return jdbc.queryForObject(
            "SELECT count(*) FROM sales_ord.order_edit_base WHERE id = ?", Integer.class, baseId)
        > 0;
  }

  private boolean receiptExists(UUID tenant, UUID operationId) {
    return jdbc.queryForObject(
            "SELECT count(*) FROM sales_ord.order_edit_operation WHERE tenant_id = ?"
                + " AND operation_id = ?",
            Integer.class,
            tenant,
            operationId)
        > 0;
  }

  private UUID receiptIdOf(UUID tenant, UUID operationId) {
    return jdbc.queryForObject(
        "SELECT id FROM sales_ord.order_edit_operation WHERE tenant_id = ? AND operation_id = ?",
        UUID.class,
        tenant,
        operationId);
  }

  private int basesIn(UUID tenant) {
    return countIn("sales_ord.order_edit_base", tenant);
  }

  private int receiptsIn(UUID tenant) {
    return countIn("sales_ord.order_edit_operation", tenant);
  }

  private int historyIn(UUID tenant) {
    return countIn("sales_ord.order_field_change", tenant);
  }

  private int countIn(String table, UUID tenant) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, tenant);
  }

  /** Moves a receipt's recording time into the past. */
  private void ageReceipt(UUID operationId, Duration age) {
    jdbc.update(
        "UPDATE sales_ord.order_edit_operation SET recorded_at = recorded_at - ?::interval"
            + " WHERE tenant_id = ? AND operation_id = ?",
        age.toSeconds() + " seconds",
        tenantId,
        operationId);
  }

  /** A connection of its own, outside the application's pool and transactions. */
  private static Connection ownerConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static void shareLock(Connection holder, String sql, UUID id) throws SQLException {
    try (PreparedStatement lock = holder.prepareStatement(sql)) {
      lock.setObject(1, id);
      lock.executeQuery().close();
    }
  }

  /** Runs a cleanup on another thread within the bound: a cleanup that waited would fail here. */
  private static <T> T bounded(Callable<T> step) {
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      return join(worker.submit(step));
    } finally {
      worker.shutdownNow();
    }
  }

  private static <T> T join(Future<T> worker) {
    try {
      return worker.get(BOUND_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException failure) {
      throw new AssertionError("A concurrent cleanup failed", failure.getCause());
    } catch (TimeoutException timeout) {
      throw new AssertionError("A cleanup did not finish in time", timeout);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }
}
