package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.sales.orderintake.app.ProductCorrectionService;
import com.fabricmanagement.sales.orderintake.app.QuantityAcceptanceService;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Safe-edit saves and the other content writers in real, overlapping PostgreSQL transactions
 * (CEDIT-02 S1.3, S5.3, S6.5, S7.5, S7.6, S11.6, S11.7, §17; CEDIT-03 §4.2 version exactness).
 *
 * <p>Every writer runs on its own worker thread, in its own transaction and persistence context, in
 * the tenant context of its actor. The order of the lock takers is fixed with latches: a writer
 * that must go first is paused while it holds its locks, either by keeping its transaction open
 * ({@link #holding}) or, for a safe save that opens its own transaction, inside its catalogue
 * check, which runs after the order row and the line rows are locked. The second writer is proven
 * to wait with {@code pg_stat_activity} before the first is released. Waits are bounded (20 s),
 * worker failures are returned to the test, and no result may be a deadlock ({@code 40P01}).
 *
 * <p>Leases are always enforced (CEDIT-07-F3): a save takes the leases it writes just for itself
 * and, applied, gives them back in its own transaction, so a writer waiting behind it finds the
 * keys free. A save that must itself wait at the order row holds an open form whose leases were
 * taken before the row was locked. The legacy full replace is closed, so its §17.3/§17.4 races are
 * gone; the lease tests prove that it is refused.
 *
 * <p>No bean is overridden here beyond the shared fixture's mocks, so this class runs in the shared
 * safe-edit Spring context. The pause points are answers of those existing mocks.
 */
class SalesOrderEditConcurrencyIT extends SalesOrderEditItSupport {

  private static final String DEADLOCK = "40P01";
  private static final String LOCK_NOT_AVAILABLE = "55P03";

  @Autowired private ProductCorrectionService productCorrections;
  @Autowired private QuantityAcceptanceService quantityAcceptances;
  @Autowired private SalesOrderLineFulfilmentLock fulfilmentLock;

  /** The product a new distribution uses (S6.5, S5.3). */
  private UUID p3;

  /** The product L1 is corrected to (S7.6, §17). */
  private UUID p9;

  private ExecutorService pool;
  private final List<Hold> holds = new CopyOnWriteArrayList<>();

  /** Pauses the next catalogue lookup: a safe save then holds its order and line locks. */
  private final AtomicReference<Hold> catalogueHold = new AtomicReference<>();

  /** Pauses the next fresh permission check: an opening base then holds its open snapshot. */
  private final AtomicReference<Hold> freshPermissionHold = new AtomicReference<>();

  /** A point where a worker stops, its transaction and locks kept, until the test lets it go. */
  private static final class Hold {
    private final CountDownLatch reached = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    void pause() {
      reached.countDown();
      await(released);
    }

    void awaitReached() {
      await(reached);
    }

    void release() {
      released.countDown();
    }
  }

  @BeforeEach
  void installPausePoints() {
    p3 = UUID.randomUUID();
    p9 = UUID.randomUUID();
    pool = Executors.newFixedThreadPool(4);
    holds.clear();
    catalogueHold.set(null);
    freshPermissionHold.set(null);
    // Same answers as the shared fixture, with one optional pause each. doAnswer does not call
    // the stubbing being replaced.
    doAnswer(
            invocation -> {
              Hold hold = catalogueHold.getAndSet(null);
              if (hold != null) {
                hold.pause();
              }
              return Optional.of(definition(invocation.getArgument(1)));
            })
        .when(productDefinitions)
        .find(any(), any());
    doAnswer(
            invocation -> {
              Hold hold = freshPermissionHold.getAndSet(null);
              if (hold != null) {
                hold.pause();
              }
              return grant(invocation.getArgument(3));
            })
        .when(permissionEvaluator)
        .evaluateFresh(any(), any(), any(), any());
  }

  @AfterEach
  void stopWorkers() throws InterruptedException {
    holds.forEach(Hold::release);
    catalogueHold.set(null);
    freshPermissionHold.set(null);
    pool.shutdownNow();
    pool.awaitTermination(20, TimeUnit.SECONDS);
  }

  // ── §1, §5, §6, §7: saves against saves and injected failures ────────────

  @Test
  @DisplayName("S1.3: two independent header saves overlap; the second waits at the order lock")
  void independentHeaderSavesOverlapAndBothApply() {
    SalesOrderEditBase baseA = open(actorA);
    SalesOrderEditBase baseB = open(actorB);
    long before = orderVersion();
    Hold aHolds = newHold();
    Map<String, Object> requestB = body(UUID.randomUUID(), baseB.baseId(), "paymentTerms", clear());
    // B's form holds its field before A locks the order, so B's save itself waits (CEDIT-07-F3).
    SalesOrderLeaseAutoProof.Held formB = holdForm(actorB, requestB);

    CompletableFuture<Object> a =
        saveHolding(
            actorA, body(UUID.randomUUID(), baseA.baseId(), "notes", set("Urgent")), aHolds);
    aHolds.awaitReached();
    CompletableFuture<Object> b = saveAsync(actorB, formB.proven(requestB));
    awaitWaiting(b, null);
    aHolds.release();

    SalesOrderEditResult first = applied(result(a));
    SalesOrderEditResult second = applied(result(b));
    assertThat(first.resultVersion()).isEqualTo(before + 1);
    assertThat(second.resultVersion()).isEqualTo(before + 2);
    assertThat(second.nextBase().orderVersion()).isEqualTo(before + 2);
    assertThat(orderVersion()).isEqualTo(before + 2);
    assertThat(orderText("notes")).isEqualTo("Urgent");
    assertThat(orderText("payment_terms")).isNull();
    assertThat(receipts("APPLIED")).isEqualTo(2);
    assertThat(receipts()).isEqualTo(2);
    assertThat(history())
        .extracting(
            row -> row.get("edit_key"),
            row -> row.get("change_kind"),
            row -> ((Number) row.get("order_version")).longValue())
        .containsExactlyInAnyOrder(
            tuple("notes", "SET", before + 1), tuple("paymentTerms", "CLEAR", before + 2));
  }

  @Test
  @DisplayName(
      "S5.3: a client_line_id uniqueness violation after the writes rolls the whole save back")
  void uniquenessViolationAfterTheWritesRollsTheSaveBack() throws Exception {
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    UUID clientLineId = UUID.randomUUID();
    UUID injected = UUID.randomUUID();
    Map<String, Object> request =
        withLines(
            body(UUID.randomUUID(), base.baseId(), "notes", set("Urgent")),
            List.of(add(clientLineId, p3, "quantity", set(quantity("200", "M")))));

    Object failed;
    try (Connection other = ownerConnection()) {
      // Another transaction writes a removed line with the same client id and keeps it
      // uncommitted: the save's pre-check cannot see it, its own insert has to wait for it. The
      // injecting session skips its FK triggers so it takes no lock on the order row.
      try (var role = other.createStatement()) {
        role.execute("SET LOCAL session_replication_role = replica");
      }
      try (var insert =
          other.prepareStatement(
              "INSERT INTO sales_ord.sales_order_line (id, tenant_id, uid, created_at, updated_at,"
                  + " is_active, version, sales_order_id, product_id, requested_qty,"
                  + " initial_requested_qty, unit, client_line_id)"
                  + " VALUES (?, ?, ?, now(), now(), false, 0, ?, ?, 200, 200, 'M', ?)")) {
        insert.setObject(1, injected);
        insert.setObject(2, tenantId);
        insert.setString(3, "SOL-INJ-" + injected);
        insert.setObject(4, orderId);
        insert.setObject(5, p3);
        insert.setObject(6, clientLineId);
        insert.executeUpdate();
      }
      CompletableFuture<Object> save = saveAsync(actorB, request);
      awaitWaiting(save, null);
      other.commit();
      failed = result(save);
    }

    assertThat(failed).isInstanceOf(RuntimeException.class).isNotInstanceOf(DomainException.class);
    assertThat(mentions(failed, "uq_sales_order_line_client_line")).isTrue();
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM sales_ord.sales_order_line WHERE client_line_id = ?",
                UUID.class,
                clientLineId))
        .containsExactly(injected);
    assertThat(activeLines()).isEqualTo(2);
    assertThat(orderText("notes")).isNull();
    assertThat(orderVersion()).isEqualTo(before);
    assertThat(receipts()).isZero();
    assertThat(historyRows()).isZero();
    assertThat(basesOf(actorB)).isEqualTo(1);
    // The rolled-back save released the order: the next save goes through at once.
    assertThat(
            saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Next")))
                .resultVersion())
        .isEqualTo(before + 1);
  }

  @Test
  @DisplayName(
      "S5.3/S10.10: a failure raised at commit, after lines, history and receipt, leaves nothing")
  void failureAtCommitAfterHistoryAndReceiptLeavesNothing() {
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    long l1Before = lineVersion(l1);
    UUID operationId = UUID.randomUUID();
    UUID clientLineId = UUID.randomUUID();
    Map<String, Object> request =
        withLines(
            body(operationId, base.baseId(), "notes", set("Urgent")),
            List.of(
                update(l1, "pricing", set(pricing("GBP", "4.5000"))),
                add(clientLineId, p3, "quantity", set(quantity("200", "M")))));
    String name = "cedit_it_fail_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    // A deferred constraint trigger fires at commit, after every write of the save was flushed.
    jdbc.execute(
        "CREATE FUNCTION sales_ord."
            + name
            + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
            + " RAISE EXCEPTION 'CEDIT-03 injected failure at commit'; END $$");
    Object failed;
    try {
      jdbc.execute(
          "CREATE CONSTRAINT TRIGGER "
              + name
              + " AFTER INSERT ON sales_ord.order_edit_operation DEFERRABLE INITIALLY DEFERRED"
              + " FOR EACH ROW WHEN (NEW.operation_id = '"
              + operationId
              + "'::uuid) EXECUTE FUNCTION sales_ord."
              + name
              + "()");
      failed = save(actorB, request);
    } finally {
      jdbc.execute("DROP TRIGGER IF EXISTS " + name + " ON sales_ord.order_edit_operation");
      jdbc.execute("DROP FUNCTION IF EXISTS sales_ord." + name + "()");
    }

    assertThat(failed).isInstanceOf(RuntimeException.class);
    assertThat(mentions(failed, "CEDIT-03 injected failure at commit")).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.sales_order_line WHERE client_line_id = ?",
                Integer.class,
                clientLineId))
        .isZero();
    assertThat(activeLines()).isEqualTo(2);
    assertThat(lineDecimal(l1, "unit_price")).isEqualByComparingTo("4.0000");
    assertThat(lineVersion(l1)).isEqualTo(l1Before);
    assertThat(orderText("notes")).isNull();
    assertThat(orderVersion()).isEqualTo(before);
    assertThat(receipts()).isZero();
    assertThat(historyRows()).isZero();
    assertThat(basesOf(actorB)).isEqualTo(1);
    assertThat(
            saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Next")))
                .resultVersion())
        .isEqualTo(before + 1);
  }

  @Test
  @DisplayName(
      "S6.5: the same distribution added twice at once: first 200, second"
          + " ORDER_INTAKE_DUPLICATE_DISTRIBUTION")
  void sameDistributionAddedTwiceConcurrentlyLeavesOneLine() {
    SalesOrderEditBase baseA = open(actorA);
    SalesOrderEditBase baseB = open(actorB);
    long before = orderVersion();
    UUID clientA = UUID.randomUUID();
    UUID clientB = UUID.randomUUID();
    Hold aHolds = newHold();

    CompletableFuture<Object> a =
        saveHolding(
            actorA,
            withLines(
                body(UUID.randomUUID(), baseA.baseId()),
                List.of(add(clientA, p3, "quantity", set(quantity("200", "M"))))),
            aHolds);
    aHolds.awaitReached();
    CompletableFuture<Object> b =
        saveAsync(
            actorB,
            withLines(
                body(UUID.randomUUID(), baseB.baseId()),
                List.of(add(clientB, p3, "quantity", set(quantity("200", "M"))))));
    awaitWaiting(b, null);
    aHolds.release();

    SalesOrderEditResult first = applied(result(a));
    Object second = result(b);
    assertThat(first.lineIds())
        .singleElement()
        .satisfies(mapping -> assertThat(mapping.clientLineId()).isEqualTo(clientA));
    assertThat(second).isNotInstanceOf(SalesOrderEditConflictException.class);
    assertThat(failureCode(second)).isEqualTo("ORDER_INTAKE_DUPLICATE_DISTRIBUTION");
    assertThat(((DomainException) second).getHttpStatus()).isEqualTo(409);
    assertThat(activeLinesOf(p3)).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(before + 1);
    assertThat(receipts()).isEqualTo(1);
    assertThat(receipts("APPLIED")).isEqualTo(1);
  }

  @Test
  @DisplayName("S7.5: removing a line whose allocation changed is LINE_CHANGED_ON_SERVER")
  void removingALineWhoseAllocationChangedConflicts() {
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    // The delivery panel moved D1's share of L2 from 300 to 250 after B's base.
    jdbc.update("UPDATE sales_ord.order_line_allocation SET quantity = 250 WHERE line_id = ?", l2);

    JsonNode conflict =
        onlyConflict(
            conflicted(
                actorB, withLines(body(UUID.randomUUID(), base.baseId()), List.of(remove(l2)))));

    assertThat(conflict.path("key").asText()).isEqualTo("line");
    assertThat(conflict.path("lineId").asText()).isEqualTo(l2.toString());
    assertThat(conflict.path("reason").asText()).isEqualTo("LINE_CHANGED_ON_SERVER");
    assertThat(conflict.path("base").has("allocationDigest")).isTrue();
    assertThat(conflict.path("current").has("allocationDigest")).isTrue();
    assertThat(conflict.path("current").path("allocationDigest").asText())
        .isNotEqualTo(conflict.path("base").path("allocationDigest").asText());
    assertThat(texts(conflict.path("choices"))).containsExactly("KEEP_CURRENT", "USE_MINE");
    assertThat(activeLines()).isEqualTo(2);
    assertThat(allocationOf(l2)).isEqualByComparingTo("250");
    assertThat(orderVersion()).isEqualTo(before);
    assertThat(receipts("CONFLICT")).isEqualTo(1);
    assertThat(historyRows()).isZero();
  }

  @Test
  @DisplayName(
      "S7.6/S14.1/S14.5: after a product correction a pricing update of the line is"
          + " LINE_PRODUCT_CHANGED")
  void pricingUpdateAfterAProductCorrectionConflicts() {
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    long l1Before = lineVersion(l1);

    assertThat(correct(actorA, l1Before)).isInstanceOf(List.class);
    // S14.1: the correction moves the aggregate version once and the line version too.
    assertThat(orderVersion()).isEqualTo(before + 1);
    assertThat(lineVersion(l1)).isEqualTo(l1Before + 1);

    JsonNode problem =
        conflicted(
            actorB,
            withLines(
                body(UUID.randomUUID(), base.baseId()),
                List.of(update(l1, "pricing", set(pricing("GBP", "4.5000"))))));

    JsonNode conflict = onlyConflict(problem);
    assertProductChanged(conflict);
    assertThat(problem.path("currentBase").path("orderVersion").asLong()).isEqualTo(before + 1);
    assertThat(lineProduct(l1)).isEqualTo(p9);
    assertThat(lineDecimal(l1, "unit_price")).isEqualByComparingTo("4.0000");
    assertThat(orderVersion()).isEqualTo(before + 1);
    assertThat(receipts("CONFLICT")).isEqualTo(1);
    assertThat(historyRows()).isZero();
  }

  // ── §11: base snapshot and the save's refresh ────────────────────────────

  @Test
  @DisplayName("S11.6: a correction committing during the opening read leaves the base all-before")
  void baseOpenedWhileACorrectionCommitsIsOneSnapshot() {
    long before = orderVersion();
    long l1Before = lineVersion(l1);
    Hold opening = newHold();
    freshPermissionHold.set(opening);

    // The opening read has read the order and stops before it reads the lines.
    CompletableFuture<Object> opened = openAsync(actorB);
    opening.awaitReached();
    assertThat(correct(actorA, l1Before)).isInstanceOf(List.class);
    assertThat(lineProduct(l1)).isEqualTo(p9);
    opening.release();

    SalesOrderEditBase base = base(result(opened));
    assertThat(base.orderVersion()).isEqualTo(before);
    assertThat(base.order().getVersion()).isEqualTo(before);
    assertThat(baseRowVersion(base.baseId())).isEqualTo(before);
    assertThat(line(base, l1).getProductId()).isEqualTo(p1);
    assertThat(line(base, l1).getVersion()).isEqualTo(l1Before);

    SalesOrderEditBase after = open(actorB);
    assertThat(after.orderVersion()).isEqualTo(before + 1);
    assertThat(line(after, l1).getProductId()).isEqualTo(p9);
    assertThat(line(after, l1).getVersion()).isEqualTo(l1Before + 1);
  }

  @Test
  @DisplayName(
      "S11.6: a base opened while a correction is uncommitted does not wait and is all-before")
  void baseOpenedBesideAnUncommittedCorrectionIsAllBefore() {
    long before = orderVersion();
    long l1Before = lineVersion(l1);
    Hold pcHolds = newHold();

    CompletableFuture<Object> pc = correctHolding(actorA, l1Before, pcHolds);
    pcHolds.awaitReached();
    // Bounded: the opening read takes no lock the correction holds, so it does not wait.
    SalesOrderEditBase base = base(result(openAsync(actorB)));
    pcHolds.release();

    assertThat(result(pc)).isInstanceOf(List.class);
    assertThat(base.orderVersion()).isEqualTo(before);
    assertThat(line(base, l1).getProductId()).isEqualTo(p1);
    assertThat(line(base, l1).getVersion()).isEqualTo(l1Before);
    assertThat(orderVersion()).isEqualTo(before + 1);
    assertThat(lineProduct(l1)).isEqualTo(p9);
  }

  @Test
  @DisplayName("S11.7: a commit between the save's read and its lock is merged from fresh values")
  void commitBetweenReadAndLockIsSeenByTheMerge() throws Exception {
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    Map<String, Object> request =
        body(UUID.randomUUID(), base.baseId(), "paymentTerms", set("60 days"));
    // The form holds its field before the order row is locked: the save itself reads, then waits
    // (CEDIT-07-F3).
    SalesOrderLeaseAutoProof.Held form = holdForm(actorB, request);
    Object answer;
    try (Connection other = ownerConnection()) {
      try (var lock =
          other.prepareStatement("SELECT id FROM sales_ord.sales_order WHERE id = ? FOR UPDATE")) {
        lock.setObject(1, orderId);
        lock.executeQuery().close();
      }
      try (var change =
          other.prepareStatement(
              "UPDATE sales_ord.sales_order SET payment_terms = ?, version = version + 1"
                  + " WHERE id = ?")) {
        change.setString(1, "45 days");
        change.setObject(2, orderId);
        change.executeUpdate();
      }
      // The save reads the order ("30 days" committed) and then waits for the order row.
      CompletableFuture<Object> save = saveAsync(actorB, form.proven(request));
      awaitWaiting(save, null);
      other.commit();
      answer = result(save);
    }

    JsonNode problem = conflictBody(answer);
    JsonNode conflict = onlyConflict(problem);
    assertThat(conflict.path("key").asText()).isEqualTo("paymentTerms");
    assertThat(conflict.path("reason").asText()).isEqualTo("CHANGED_ON_SERVER");
    assertThat(conflict.path("base").asText()).isEqualTo("30 days");
    assertThat(conflict.path("current").asText()).isEqualTo("45 days");
    assertThat(conflict.path("mine").asText()).isEqualTo("60 days");
    assertThat(problem.path("currentBase").path("orderVersion").asLong()).isEqualTo(before + 1);
    assertThat(orderText("payment_terms")).isEqualTo("45 days");
    assertThat(orderVersion()).isEqualTo(before + 1);
    assertThat(receipts("CONFLICT")).isEqualTo(1);
    assertThat(historyRows()).isZero();
  }

  // ── §17: lock order against the other writers, both commit orders ────────

  @Test
  @DisplayName("S17.1 (correction first): a save of L2 waits at the root, then applies; +2")
  void correctionFirstThenSaveOfAnotherLine() {
    long l1Before = lineVersion(l1);
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    Hold pcHolds = newHold();
    Map<String, Object> request =
        withLines(
            body(UUID.randomUUID(), base.baseId()),
            List.of(update(l2, "tolerance", set(tolerance("3", "3")))));
    // B's form holds L2's tolerance before the correction starts; the correction changes only L1
    // and is not stopped by it. B's save itself waits at the root (CEDIT-07-F3).
    SalesOrderLeaseAutoProof.Held form = holdForm(actorB, request);

    CompletableFuture<Object> pc = correctHolding(actorA, l1Before, pcHolds);
    pcHolds.awaitReached();
    CompletableFuture<Object> save = saveAsync(actorB, form.proven(request));
    awaitWaiting(save, null);
    pcHolds.release();

    assertThat(result(pc)).isInstanceOf(List.class);
    SalesOrderEditResult saved = applied(result(save));
    assertThat(saved.resultVersion()).isEqualTo(before + 2);
    assertThat(orderVersion()).isEqualTo(before + 2);
    assertThat(lineProduct(l1)).isEqualTo(p9);
    assertThat(lineDecimal(l2, "tolerance_up_pct")).isEqualByComparingTo("3");
    assertThat(corrections()).isEqualTo(1);
  }

  @Test
  @DisplayName("S17.1 (save first): the correction waits at the root, then applies; +2")
  void saveOfAnotherLineFirstThenCorrection() {
    long l1Before = lineVersion(l1);
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    Hold saveHolds = newHold();

    CompletableFuture<Object> save =
        saveHolding(
            actorB,
            withLines(
                body(UUID.randomUUID(), base.baseId()),
                List.of(update(l2, "tolerance", set(tolerance("3", "3"))))),
            saveHolds);
    saveHolds.awaitReached();
    CompletableFuture<Object> pc = correctAsync(actorA, l1Before);
    awaitWaiting(pc, null);
    saveHolds.release();

    assertThat(applied(result(save)).resultVersion()).isEqualTo(before + 1);
    assertThat(result(pc)).isInstanceOf(List.class);
    assertThat(orderVersion()).isEqualTo(before + 2);
    assertThat(lineProduct(l1)).isEqualTo(p9);
    assertThat(lineVersion(l1)).isEqualTo(l1Before + 1);
    assertThat(lineDecimal(l2, "tolerance_up_pct")).isEqualByComparingTo("3");
    assertThat(corrections()).isEqualTo(1);
  }

  @Test
  @DisplayName("S17.2 (correction first): a pricing save of L1 is LINE_PRODUCT_CHANGED")
  void correctionFirstThenPricingSaveOfTheSameLine() {
    long l1Before = lineVersion(l1);
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    Hold pcHolds = newHold();

    CompletableFuture<Object> pc = correctHolding(actorA, l1Before, pcHolds);
    pcHolds.awaitReached();
    CompletableFuture<Object> save =
        saveAsync(
            actorB,
            withLines(
                body(UUID.randomUUID(), base.baseId()),
                List.of(update(l1, "pricing", set(pricing("GBP", "4.5000"))))));
    awaitWaiting(save, null);
    pcHolds.release();

    assertThat(result(pc)).isInstanceOf(List.class);
    assertProductChanged(onlyConflict(conflictBody(result(save))));
    assertThat(lineProduct(l1)).isEqualTo(p9);
    assertThat(lineDecimal(l1, "unit_price")).isEqualByComparingTo("4.0000");
    assertThat(orderVersion()).isEqualTo(before + 1);
    assertThat(receipts("CONFLICT")).isEqualTo(1);
    assertThat(historyRows()).isZero();
  }

  @Test
  @DisplayName("S17.2 (save first): the correction waits, then fails ORDER_INTAKE_STALE_VERSION")
  void pricingSaveFirstThenCorrectionOfTheSameLine() {
    long l1Before = lineVersion(l1);
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    Hold saveHolds = newHold();

    CompletableFuture<Object> save =
        saveHolding(
            actorB,
            withLines(
                body(UUID.randomUUID(), base.baseId()),
                List.of(update(l1, "pricing", set(pricing("GBP", "4.5000"))))),
            saveHolds);
    saveHolds.awaitReached();
    CompletableFuture<Object> pc = correctAsync(actorA, l1Before);
    awaitWaiting(pc, null);
    saveHolds.release();

    assertThat(applied(result(save)).resultVersion()).isEqualTo(before + 1);
    Object refused = result(pc);
    assertThat(failureCode(refused)).isEqualTo("ORDER_INTAKE_STALE_VERSION");
    assertThat(((DomainException) refused).getHttpStatus()).isEqualTo(409);
    assertThat(lineProduct(l1)).isEqualTo(p1);
    assertThat(lineDecimal(l1, "unit_price")).isEqualByComparingTo("4.5000");
    assertThat(corrections()).isZero();
    assertThat(orderVersion()).isEqualTo(before + 1);
  }

  @Test
  @DisplayName(
      "S17.5 (correction first): the acceptance waits, then decides on the corrected L1;"
          + " no quantity is written")
  void correctionFirstThenAcceptanceWithdrawalOfTheSameLine() {
    UUID acceptance = activeAcceptanceOnL1();
    long l1Before = lineVersion(l1);
    long before = orderVersion();
    Hold pcHolds = newHold();

    CompletableFuture<Object> pc = correctHolding(actorA, l1Before, pcHolds);
    pcHolds.awaitReached();
    CompletableFuture<Object> withdrawal = async(() -> withdrawAcceptance(actorC));
    awaitWaiting(withdrawal, null);
    pcHolds.release();

    assertThat(result(pc)).isInstanceOf(List.class);
    // The correction withdrew the acceptance; after its lock the acceptance service reads that,
    // not the state before it waited, and writes no quantity on the P1 assumption.
    Object refused = result(withdrawal);
    assertThat(failureCode(refused)).isEqualTo("ORDER_INTAKE_NOT_FOUND");
    assertThat(((DomainException) refused).getHttpStatus()).isEqualTo(404);
    assertThat(acceptanceStatus(acceptance)).isEqualTo("WITHDRAWN");
    assertThat(lineProduct(l1)).isEqualTo(p9);
    assertThat(lineDecimal(l1, "requested_qty")).isEqualByComparingTo("1020");
    assertThat(lineVersion(l1)).isEqualTo(l1Before + 1);
    assertThat(orderVersion()).isEqualTo(before + 1);
  }

  @Test
  @DisplayName(
      "S17.5 (acceptance first): the correction waits, then fails ORDER_INTAKE_STALE_VERSION")
  void acceptanceWithdrawalFirstThenCorrectionOfTheSameLine() {
    UUID acceptance = activeAcceptanceOnL1();
    long l1Before = lineVersion(l1);
    long before = orderVersion();
    Hold qaHolds = newHold();

    CompletableFuture<Object> withdrawal = withdrawHolding(actorC, qaHolds);
    qaHolds.awaitReached();
    CompletableFuture<Object> pc = correctAsync(actorA, l1Before);
    awaitWaiting(pc, null);
    qaHolds.release();

    assertThat(result(withdrawal)).isEqualTo("WITHDRAWN");
    assertThat(failureCode(result(pc))).isEqualTo("ORDER_INTAKE_STALE_VERSION");
    assertThat(acceptanceStatus(acceptance)).isEqualTo("WITHDRAWN");
    assertThat(lineProduct(l1)).isEqualTo(p1);
    assertThat(lineDecimal(l1, "requested_qty")).isEqualByComparingTo("1000");
    assertThat(lineVersion(l1)).isEqualTo(l1Before + 1);
    assertThat(corrections()).isZero();
    assertThat(orderVersion()).isEqualTo(before + 1);
  }

  @Test
  @DisplayName(
      "S17.6 (acceptance first): a quantity save of L1 is CHANGED_ON_SERVER on line.quantity")
  void acceptanceWithdrawalFirstThenQuantitySave() {
    UUID acceptance = activeAcceptanceOnL1();
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    Hold qaHolds = newHold();

    CompletableFuture<Object> withdrawal = withdrawHolding(actorC, qaHolds);
    qaHolds.awaitReached();
    CompletableFuture<Object> save =
        saveAsync(
            actorB,
            withLines(
                body(UUID.randomUUID(), base.baseId()),
                List.of(update(l1, "quantity", set(quantity("1100", "M"))))));
    awaitWaiting(save, null);
    qaHolds.release();

    assertThat(result(withdrawal)).isEqualTo("WITHDRAWN");
    JsonNode conflict = onlyConflict(conflictBody(result(save)));
    assertThat(conflict.path("key").asText()).isEqualTo("line.quantity");
    assertThat(conflict.path("lineId").asText()).isEqualTo(l1.toString());
    assertThat(conflict.path("reason").asText()).isEqualTo("CHANGED_ON_SERVER");
    assertThat(decimal(conflict.path("base").path("requestedQty"))).isEqualByComparingTo("1020");
    assertThat(decimal(conflict.path("current").path("requestedQty"))).isEqualByComparingTo("1000");
    assertThat(decimal(conflict.path("mine").path("requestedQty"))).isEqualByComparingTo("1100");
    assertThat(acceptanceStatus(acceptance)).isEqualTo("WITHDRAWN");
    assertThat(lineDecimal(l1, "requested_qty")).isEqualByComparingTo("1000");
    assertThat(orderVersion()).isEqualTo(before + 1);
    assertThat(receipts("CONFLICT")).isEqualTo(1);
  }

  @Test
  @DisplayName("S17.6 (save first): the acceptance waits, then decides on the saved quantity of L1")
  void quantitySaveFirstThenAcceptanceWithdrawal() {
    UUID acceptance = activeAcceptanceOnL1();
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    long l1Before = lineVersion(l1);
    Hold saveHolds = newHold();

    // The save returns L1 to the customer's request (1000 M) while the acceptance stood at 1020.
    CompletableFuture<Object> save =
        saveHolding(
            actorB,
            withLines(
                body(UUID.randomUUID(), base.baseId()),
                List.of(update(l1, "quantity", set(quantity("1000", "M"))))),
            saveHolds);
    saveHolds.awaitReached();
    CompletableFuture<Object> withdrawal = async(() -> withdrawAcceptance(actorC));
    awaitWaiting(withdrawal, null);
    saveHolds.release();

    assertThat(applied(result(save)).resultVersion()).isEqualTo(before + 1);
    assertThat(result(withdrawal)).isEqualTo("WITHDRAWN");
    // Judged on the refreshed line (1000 = the initial request): the acceptance is withdrawn and
    // neither the line nor the order version moves again. A stale 1020 would have moved both.
    assertThat(acceptanceStatus(acceptance)).isEqualTo("WITHDRAWN");
    assertThat(lineDecimal(l1, "requested_qty")).isEqualByComparingTo("1000");
    assertThat(lineVersion(l1)).isEqualTo(l1Before + 1);
    assertThat(orderVersion()).isEqualTo(before + 1);
  }

  @Test
  @DisplayName("S17.7 (correction first): a stock reservation waits on the advisory line lock only")
  void correctionFirstThenStockReservationOfTheSameLine() {
    long l1Before = lineVersion(l1);
    long before = orderVersion();
    Hold pcHolds = newHold();

    CompletableFuture<Object> pc = correctHolding(actorA, l1Before, pcHolds);
    pcHolds.awaitReached();
    CompletableFuture<Object> reservation = reserveAsync(actorB, null);
    awaitWaiting(reservation, "advisory");
    pcHolds.release();

    assertThat(result(pc)).isInstanceOf(List.class);
    assertThat(result(reservation)).isEqualTo("RESERVED");
    assertThat(lineProduct(l1)).isEqualTo(p9);
    assertThat(orderVersion()).isEqualTo(before + 1);
  }

  @Test
  @DisplayName(
      "S17.7 (reservation first): the correction holds the root and waits on the advisory lock")
  void stockReservationFirstThenCorrectionOfTheSameLine() throws Exception {
    long l1Before = lineVersion(l1);
    long before = orderVersion();
    Hold reservationHolds = newHold();

    CompletableFuture<Object> reservation = reserveAsync(actorB, reservationHolds);
    reservationHolds.awaitReached();
    assertThat(orderRowIsFree()).as("the reservation takes no root lock").isTrue();
    CompletableFuture<Object> pc = correctAsync(actorA, l1Before);
    awaitWaiting(pc, "advisory");
    assertThat(orderRowIsFree()).as("the waiting correction holds the root").isFalse();
    reservationHolds.release();

    assertThat(result(reservation)).isEqualTo("RESERVED");
    assertThat(result(pc)).isInstanceOf(List.class);
    assertThat(lineProduct(l1)).isEqualTo(p9);
    assertThat(orderVersion()).isEqualTo(before + 1);
  }

  @Test
  @DisplayName(
      "S17.8: the correction refreshes an order loaded before its lock: ORDER_WITH_PLANNING")
  void correctionJudgesTheRefreshedOrderNotAStaleManagedOne() {
    long l1Before = lineVersion(l1);
    long before = orderVersion();
    AtomicReference<OrderFlowStage> seenBeforeTheLock = new AtomicReference<>();

    Object refused =
        as(
            actorA,
            () ->
                transactions.execute(
                    status -> {
                      SalesOrder stale =
                          orders.findByTenantIdAndId(tenantId, orderId).orElseThrow();
                      seenBeforeTheLock.set(stale.getFlowStage());
                      commitElsewhere(this::sendToPlanning);
                      return productCorrections.correct(orderId, correction(l1Before), actorA.id());
                    }));

    assertThat(seenBeforeTheLock.get()).isEqualTo(OrderFlowStage.DRAFT);
    assertThat(failureCode(refused)).isEqualTo("ORDER_WITH_PLANNING");
    assertThat(lineProduct(l1)).isEqualTo(p1);
    assertThat(corrections()).isZero();
    assertThat(orderVersion()).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "S17.8: the acceptance service refreshes an order loaded before its lock:"
          + " ORDER_WITH_PLANNING")
  void acceptanceJudgesTheRefreshedOrderNotAStaleManagedOne() {
    UUID acceptance = activeAcceptanceOnL1();
    long before = orderVersion();
    AtomicReference<OrderFlowStage> seenBeforeTheLock = new AtomicReference<>();

    Object refused =
        as(
            actorC,
            () ->
                transactions.execute(
                    status -> {
                      SalesOrder stale =
                          orders.findByTenantIdAndId(tenantId, orderId).orElseThrow();
                      seenBeforeTheLock.set(stale.getFlowStage());
                      commitElsewhere(this::sendToPlanning);
                      quantityAcceptances.withdraw(orderId, l1, actorC.id());
                      return "WITHDRAWN";
                    }));

    assertThat(seenBeforeTheLock.get()).isEqualTo(OrderFlowStage.DRAFT);
    assertThat(failureCode(refused)).isEqualTo("ORDER_WITH_PLANNING");
    assertThat(acceptanceStatus(acceptance)).isEqualTo("ACTIVE");
    assertThat(lineDecimal(l1, "requested_qty")).isEqualByComparingTo("1020");
    assertThat(orderVersion()).isEqualTo(before);
  }

  @Test
  @DisplayName("S17.9: a product correction moves the aggregate version exactly once")
  void correctionMovesTheVersionExactlyOnce() {
    long before = orderVersion();
    long l1Before = lineVersion(l1);

    // Bounded: a correction that waited for its own root lock would not finish.
    assertThat(result(correctAsync(actorA, l1Before))).isInstanceOf(List.class);

    assertThat(orderVersion()).isEqualTo(before + 1);
    assertThat(lineVersion(l1)).isEqualTo(l1Before + 1);
    assertThat(lineProduct(l1)).isEqualTo(p9);
    assertThat(corrections()).isEqualTo(1);
    assertThat(open(actorB).orderVersion()).isEqualTo(before + 1);
  }

  // ── CEDIT-03 §4.2: the version moves exactly once, or not at all ─────────

  @Test
  @DisplayName("S1.1/S10.2: a header-only save moves the version by 1, its replay by 0")
  void headerOnlySaveMovesTheVersionOnceAndItsReplayNot() {
    SalesOrderEditBase base = open(actorA);
    long before = orderVersion();
    UUID operationId = UUID.randomUUID();
    Map<String, Object> request = body(operationId, base.baseId(), "notes", set("Urgent"));

    SalesOrderEditResult result = saved(actorA, request);
    assertMovedExactlyOnce(result, before, operationId);
    int historyAfterSave = historyRows();

    SalesOrderEditResult replay = saved(actorA, request);
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(replay.resultVersion()).isEqualTo(before + 1);
    assertThat(replay.nextBase().orderVersion()).isEqualTo(before + 1);
    assertThat(orderVersion()).isEqualTo(before + 1);
    assertThat(historyRows()).isEqualTo(historyAfterSave);
    assertThat(receipts()).isEqualTo(1);
  }

  @Test
  @DisplayName("S6.2: a line-only save moves the order version by exactly 1")
  void lineOnlySaveMovesTheVersionOnce() {
    SalesOrderEditBase base = open(actorA);
    long before = orderVersion();
    UUID operationId = UUID.randomUUID();

    SalesOrderEditResult result =
        saved(
            actorA,
            withLines(
                body(operationId, base.baseId()),
                List.of(update(l2, "tolerance", set(tolerance("3", "3"))))));

    assertMovedExactlyOnce(result, before, operationId);
    assertThat(lineDecimal(l2, "tolerance_up_pct")).isEqualByComparingTo("3");
  }

  @Test
  @DisplayName("S6.1/S10.11: a mixed header, line and added-line save moves the version by 1")
  void mixedSaveMovesTheVersionOnce() {
    SalesOrderEditBase base = open(actorA);
    long before = orderVersion();
    UUID operationId = UUID.randomUUID();
    UUID clientLineId = UUID.randomUUID();

    SalesOrderEditResult result =
        saved(
            actorA,
            withLines(
                body(operationId, base.baseId(), "notes", set("Urgent")),
                List.of(
                    update(l1, "pricing", set(pricing("GBP", "4.2000"))),
                    add(clientLineId, p3, "quantity", set(quantity("200", "M"))))));

    assertMovedExactlyOnce(result, before, operationId);
    assertThat(result.lineIds())
        .singleElement()
        .satisfies(mapping -> assertThat(mapping.clientLineId()).isEqualTo(clientLineId));
    assertThat(history())
        .extracting(row -> row.get("edit_key"), row -> row.get("change_kind"))
        .containsExactlyInAnyOrder(
            tuple("notes", "SET"), tuple("line.pricing", "SET"), tuple("line", "LINE_ADDED"));
    assertThat(activeLines()).isEqualTo(3);
  }

  @Test
  @DisplayName("S3.2/S10.2: NO_CHANGE and its replay leave the order version where it was")
  void noChangeAndItsReplayDoNotMoveTheVersion() {
    SalesOrderEditBase base = open(actorA);
    long before = orderVersion();
    Map<String, Object> request =
        body(UUID.randomUUID(), base.baseId(), "paymentTerms", set("30 days"));

    SalesOrderEditResult result = saved(actorA, request);
    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(result.resultVersion()).isEqualTo(before);
    assertThat(result.nextBase().orderVersion()).isEqualTo(before);
    assertThat(orderVersion()).isEqualTo(before);

    SalesOrderEditResult replay = saved(actorA, request);
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(replay.resultVersion()).isEqualTo(before);
    assertThat(orderVersion()).isEqualTo(before);
    assertThat(receipts("NO_CHANGE")).isEqualTo(1);
    assertThat(historyRows()).isZero();
  }

  // ── writers ───────────────────────────────────────────────────────────────

  private Hold newHold() {
    Hold hold = new Hold();
    holds.add(hold);
    return hold;
  }

  private CompletableFuture<Object> async(Supplier<Object> step) {
    return CompletableFuture.supplyAsync(step, pool);
  }

  private CompletableFuture<Object> saveAsync(Actor actor, Map<String, Object> body) {
    return async(() -> save(actor, body));
  }

  private CompletableFuture<Object> openAsync(Actor actor) {
    return async(
        () -> as(actor, () -> edits.openBase(orderId, actor.id(), actor.authentication())));
  }

  /**
   * A safe save that stops in its catalogue check, after it locked and refreshed the order row and
   * the line rows and before it flushes: its transaction holds the locks until released.
   */
  private CompletableFuture<Object> saveHolding(Actor actor, Map<String, Object> body, Hold hold) {
    catalogueHold.set(hold);
    return saveAsync(actor, body);
  }

  /** Runs a step in a transaction that stays open, with the locks it took, until released. */
  private <T> T holding(Hold hold, Supplier<T> step) {
    return transactions.execute(
        status -> {
          try {
            return step.get();
          } finally {
            hold.pause();
          }
        });
  }

  private FulfilmentDtos.CorrectProduct correction(long l1Version) {
    return new FulfilmentDtos.CorrectProduct(
        p1, p9, List.of(new FulfilmentDtos.LineVersion(l1, l1Version)), "Wrong article chosen");
  }

  private Object correct(Actor actor, long l1Version) {
    return as(actor, () -> productCorrections.correct(orderId, correction(l1Version), actor.id()));
  }

  private CompletableFuture<Object> correctAsync(Actor actor, long l1Version) {
    return async(() -> correct(actor, l1Version));
  }

  private CompletableFuture<Object> correctHolding(Actor actor, long l1Version, Hold hold) {
    return async(
        () ->
            as(
                actor,
                () ->
                    holding(
                        hold,
                        () ->
                            productCorrections.correct(
                                orderId, correction(l1Version), actor.id()))));
  }

  private Object withdrawAcceptance(Actor actor) {
    return as(
        actor,
        () -> {
          quantityAcceptances.withdraw(orderId, l1, actor.id());
          return "WITHDRAWN";
        });
  }

  private CompletableFuture<Object> withdrawHolding(Actor actor, Hold hold) {
    return async(
        () ->
            as(
                actor,
                () ->
                    holding(
                        hold,
                        () -> {
                          quantityAcceptances.withdraw(orderId, l1, actor.id());
                          return "WITHDRAWN";
                        })));
  }

  /** A stock reservation's lock: only the advisory line lock, as reservations take it. */
  private CompletableFuture<Object> reserveAsync(Actor actor, Hold hold) {
    return async(
        () ->
            as(
                actor,
                () ->
                    transactions.execute(
                        status -> {
                          try {
                            fulfilmentLock.lockAll(tenantId, List.of(l1));
                            return "RESERVED";
                          } finally {
                            if (hold != null) {
                              hold.pause();
                            }
                          }
                        })));
  }

  /** Runs a step on another thread and waits until it committed. */
  private void commitElsewhere(Runnable step) {
    try {
      CompletableFuture.runAsync(step, pool).get(20, TimeUnit.SECONDS);
    } catch (ExecutionException failure) {
      throw new IllegalStateException(failure.getCause());
    } catch (TimeoutException failure) {
      throw new IllegalStateException("The other transaction did not commit", failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  // ── fixture additions ─────────────────────────────────────────────────────

  /**
   * L1 as a recorded customer acceptance left it (SOI D3): the customer took 1020 M of whole pieces
   * against the requested 1000 M; the acceptance is ACTIVE.
   */
  private UUID activeAcceptanceOnL1() {
    jdbc.update(
        "UPDATE sales_ord.sales_order_line SET requested_qty = 1020, initial_requested_qty = 1000"
            + " WHERE id = ?",
        l1);
    UUID proposal = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO sales_ord.quantity_proposal (id, tenant_id, created_at, updated_at,"
            + " is_active, version, sales_order_id, sales_order_line_id, product_id,"
            + " requested_qty, unit, evaluation_status, evidence_fingerprint, result,"
            + " evaluated_by, evaluated_at)"
            + " VALUES (?, ?, now(), now(), true, 0, ?, ?, ?, 1000, 'M', 'OPTIONS', ?,"
            + " '{\"status\":\"OPTIONS\",\"options\":[]}'::jsonb, ?, now())",
        proposal,
        tenantId,
        orderId,
        l1,
        p1,
        "ab".repeat(32),
        actorA.id());
    UUID acceptance = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO sales_ord.quantity_acceptance (id, tenant_id, created_at, updated_at,"
            + " is_active, version, sales_order_id, sales_order_line_id, proposal_id, option_key,"
            + " option_kind, compatibility, accepted_qty, canonical_qty, unit, piece_ids,"
            + " batch_ids, basis, conditional, remnant_acknowledged, customer_contact, channel,"
            + " accepted_at, customer_statement_confirmed, terms_fingerprint, status,"
            + " recorded_by, recorded_at)"
            + " VALUES (?, ?, now(), now(), true, 0, ?, ?, ?, 'ABOVE-1', 'ABOVE', 'SINGLE_LOT',"
            + " 1020, 1020, 'M', ?::jsonb, ?::jsonb, 'CUSTOMER_ACCEPTED', false, false,"
            + " 'Jane Hill', 'EMAIL', now(), true, ?, 'ACTIVE', ?, now())",
        acceptance,
        tenantId,
        orderId,
        l1,
        proposal,
        "[\"" + UUID.randomUUID() + "\"]",
        "[\"" + UUID.randomUUID() + "\"]",
        "cd".repeat(32),
        actorA.id());
    return acceptance;
  }

  private static ProductSalesDefinitionDto definition(UUID productId) {
    return new ProductSalesDefinitionDto(
        productId,
        "PRD-" + productId.toString().substring(0, 8),
        "Test fabric",
        ProductType.FABRIC,
        "M",
        true,
        List.of(),
        List.of());
  }

  private PermissionResult grant(UUID userId) {
    DataScope scope = userId == null ? null : scopes.get(userId);
    if (scope == null) {
      return new PermissionResult(Map.of(), false);
    }
    return new PermissionResult(Map.of("sales", Map.of("read", scope, "write", scope)), false);
  }

  // ── waiting and results ───────────────────────────────────────────────────

  /**
   * Polls until a backend waits for a lock (of the given wait event when one is named), within a
   * bound; fails at once when the step that should wait already finished.
   */
  private void awaitWaiting(CompletableFuture<Object> waiter, String waitEvent) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (!waiting(waitEvent)) {
      if (waiter.isDone()) {
        throw new AssertionError(
            "Expected the step to wait for a lock, but it finished: " + waiter.getNow(null));
      }
      if (System.nanoTime() > deadline) {
        throw new AssertionError("No transaction started waiting for the lock");
      }
      Thread.onSpinWait();
    }
  }

  private boolean waiting(String waitEvent) {
    if (waitEvent == null) {
      return someoneWaitsForALock();
    }
    Integer waiters =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                + " AND wait_event_type = 'Lock' AND wait_event = ?",
            Integer.class,
            waitEvent);
    return waiters != null && waiters > 0;
  }

  /** A worker's result within the bound; a worker error fails the test; never a deadlock. */
  private static Object result(CompletableFuture<Object> future) {
    Object result;
    try {
      result = future.get(20, TimeUnit.SECONDS);
    } catch (ExecutionException failure) {
      if (failure.getCause() instanceof Error error) {
        throw error;
      }
      throw new AssertionError("The worker failed", failure.getCause());
    } catch (TimeoutException failure) {
      throw new AssertionError("The worker did not finish within 20 s", failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
    assertNoDeadlock(result);
    return result;
  }

  private static void assertNoDeadlock(Object result) {
    for (Throwable cause = result instanceof Throwable thrown ? thrown : null;
        cause != null;
        cause = cause.getCause()) {
      boolean deadlock =
          (cause instanceof SQLException sql && DEADLOCK.equals(sql.getSQLState()))
              || (cause.getMessage() != null && cause.getMessage().contains("deadlock detected"));
      if (deadlock) {
        throw new AssertionError("A transaction ended in a deadlock (40P01)", cause);
      }
    }
  }

  /** True when the failure or one of its causes carries the text. */
  private static boolean mentions(Object result, String text) {
    for (Throwable cause = result instanceof Throwable thrown ? thrown : null;
        cause != null;
        cause = cause.getCause()) {
      if (cause.getMessage() != null && cause.getMessage().contains(text)) {
        return true;
      }
      if (cause instanceof SQLException sql
          && sql.getNextException() != null
          && sql.getNextException().getMessage() != null
          && sql.getNextException().getMessage().contains(text)) {
        return true;
      }
    }
    return false;
  }

  private static SalesOrderEditResult applied(Object result) {
    assertThat(result).as("a saved result").isInstanceOf(SalesOrderEditResult.class);
    SalesOrderEditResult saved = (SalesOrderEditResult) result;
    assertThat(saved.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(saved.replayed()).isFalse();
    return saved;
  }

  private static SalesOrderEditBase base(Object result) {
    assertThat(result).as("an opened base").isInstanceOf(SalesOrderEditBase.class);
    return (SalesOrderEditBase) result;
  }

  private static JsonNode conflictBody(Object result) {
    assertThat(result)
        .as("a recorded conflict")
        .isInstanceOf(SalesOrderEditConflictException.class);
    return ((SalesOrderEditConflictException) result).body();
  }

  /** A decimal of a problem body, whether written as a JSON number or as text. */
  private static BigDecimal decimal(JsonNode value) {
    return new BigDecimal(value.asText());
  }

  private static List<String> texts(JsonNode array) {
    List<String> texts = new ArrayList<>();
    array.forEach(item -> texts.add(item.asText()));
    return texts;
  }

  private static JsonNode onlyConflict(JsonNode problem) {
    assertThat(problem.path("code").asText()).isEqualTo("EDIT_CONFLICT");
    assertThat(problem.path("conflicts").size()).isEqualTo(1);
    return problem.path("conflicts").get(0);
  }

  /** L1's product was corrected P1 → P9 since the base; the whole line is in conflict. */
  private void assertProductChanged(JsonNode conflict) {
    assertThat(conflict.path("key").asText()).isEqualTo("line");
    assertThat(conflict.path("lineId").asText()).isEqualTo(l1.toString());
    assertThat(conflict.path("reason").asText()).isEqualTo("LINE_PRODUCT_CHANGED");
    assertThat(conflict.path("base").path("productId").asText()).isEqualTo(p1.toString());
    assertThat(conflict.path("current").path("productId").asText()).isEqualTo(p9.toString());
    assertThat(conflict.path("mine").path("lineId").asText()).isEqualTo(l1.toString());
    assertThat(texts(conflict.path("choices")))
        .containsExactly("KEEP_CURRENT", "USE_MINE", "NEW_VALUE");
  }

  /**
   * An applied save moved the order version by exactly one: the result, the committed row, the next
   * base and every history row of the operation carry the same version.
   */
  private void assertMovedExactlyOnce(SalesOrderEditResult result, long before, UUID operationId) {
    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(result.replayed()).isFalse();
    assertThat(result.resultVersion()).isEqualTo(before + 1);
    assertThat(orderVersion()).isEqualTo(result.resultVersion());
    assertThat(result.nextBase().orderVersion()).isEqualTo(result.resultVersion());
    assertThat(result.nextBase().order().getVersion()).isEqualTo(result.resultVersion());
    assertThat(history())
        .filteredOn(row -> operationId.equals(row.get("operation_id")))
        .isNotEmpty()
        .allSatisfy(
            row ->
                assertThat(((Number) row.get("order_version")).longValue())
                    .isEqualTo(result.resultVersion()));
  }

  // ── database reads ────────────────────────────────────────────────────────

  private static Connection ownerConnection() throws SQLException {
    Connection connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    connection.setAutoCommit(false);
    return connection;
  }

  /** Whether the order row can be locked right now by someone else (NOWAIT probe). */
  private boolean orderRowIsFree() throws SQLException {
    try (Connection probe = ownerConnection();
        var statement =
            probe.prepareStatement(
                "SELECT id FROM sales_ord.sales_order WHERE id = ? FOR UPDATE NOWAIT")) {
      statement.setObject(1, orderId);
      statement.executeQuery().close();
      probe.rollback();
      return true;
    } catch (SQLException failure) {
      if (LOCK_NOT_AVAILABLE.equals(failure.getSQLState())) {
        return false;
      }
      throw failure;
    }
  }

  private static SalesOrderLineResponse line(SalesOrderEditBase base, UUID lineId) {
    return base.order().getLines().stream()
        .filter(line -> lineId.equals(line.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Line " + lineId + " is not in the base"));
  }

  private UUID lineProduct(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT product_id FROM sales_ord.sales_order_line WHERE id = ?", UUID.class, lineId);
  }

  private BigDecimal lineDecimal(UUID lineId, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM sales_ord.sales_order_line WHERE id = ?",
        BigDecimal.class,
        lineId);
  }

  private int activeLinesOf(UUID productId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.sales_order_line"
            + " WHERE sales_order_id = ? AND product_id = ? AND is_active = true",
        Integer.class,
        orderId,
        productId);
  }

  private BigDecimal allocationOf(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT quantity FROM sales_ord.order_line_allocation"
            + " WHERE line_id = ? AND is_active = true",
        BigDecimal.class,
        lineId);
  }

  private int corrections() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.line_product_correction WHERE sales_order_line_id = ?",
        Integer.class,
        l1);
  }

  private String acceptanceStatus(UUID acceptanceId) {
    return jdbc.queryForObject(
        "SELECT status FROM sales_ord.quantity_acceptance WHERE id = ?",
        String.class,
        acceptanceId);
  }

  private int basesOf(Actor actor) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.order_edit_base WHERE sales_order_id = ? AND actor_id = ?",
        Integer.class,
        orderId,
        actor.id());
  }

  private long baseRowVersion(UUID baseId) {
    return jdbc.queryForObject(
        "SELECT order_version FROM sales_ord.order_edit_base WHERE id = ?", Long.class, baseId);
  }

  private static Map<String, Object> tolerance(String upPct, String downPct) {
    return pairs("upPct", new BigDecimal(upPct), "downPct", new BigDecimal(downPct));
  }
}
