package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLineIdMapping;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOperationView;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Repeats and atomic history of the safe edit (CEDIT-02 §10, CEDIT-03 §4.3) on real PostgreSQL:
 * lost answers, the same operation raced in two transactions, an operation id reused on another
 * order or with other content, conflict replays, and a failure before commit that leaves nothing.
 *
 * <p>Request R is B's save of {@code ADD c-b (P3, 300 M)} with {@code notes SET("Urgent")}.
 */
class SalesOrderEditReplayIT extends SalesOrderEditItSupport {

  private static final long BOUND_SECONDS = 20;

  /** Values compare by meaning: 7 and 7L, or 4.0000 and 4.0, are the same number. */
  private static final Comparator<JsonNode> SAME_VALUE =
      (left, right) -> {
        if (left.isNumber() && right.isNumber()) {
          return left.decimalValue().compareTo(right.decimalValue());
        }
        return left.equals(right) ? 0 : 1;
      };

  private UUID p3;
  private UUID cb;

  @BeforeEach
  void prepareRequestR() {
    p3 = UUID.randomUUID();
    cb = UUID.randomUUID();
  }

  // ── S10.1 / S10.2 / S10.6 ────────────────────────────────────────────────

  @Test
  @DisplayName("S10.1: a failing request repeated fails the same way and leaves no receipt")
  void failingRequestRepeatedFailsAlikeWithoutReceipt() {
    when(productDefinitions.find(any(), eq(p3))).thenReturn(Optional.empty());
    SalesOrderEditBase base = open(actorB);
    long version = orderVersion();
    UUID operationId = UUID.randomUUID();

    Object first = save(actorB, requestR(operationId, base.baseId()));
    Object second = save(actorB, requestR(operationId, base.baseId()));

    assertThat(failureCode(first)).isEqualTo("ORDER_INTAKE_PRODUCT_NOT_AVAILABLE");
    assertThat(failureCode(second)).isEqualTo(failureCode(first));
    assertThat(((DomainException) first).getHttpStatus()).isEqualTo(422);
    assertThat(((DomainException) second).getHttpStatus()).isEqualTo(422);
    assertThat(receipts()).isZero();
    assertThat(historyRows()).isZero();
    assertThat(activeLines()).isEqualTo(2);
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(orderText("notes")).isNull();
    assertThat(basesOfOrder(orderId, "SAVED")).isZero();
  }

  @Test
  @DisplayName(
      "S10.2: the same request after a lost answer replays: same lineIds and resultVersion,"
          + " one line, one receipt, no new history")
  void lostAnswerReplays() {
    SalesOrderEditBase base = open(actorB);
    long version = orderVersion();
    UUID operationId = UUID.randomUUID();

    SalesOrderEditResult first = saved(actorB, requestR(operationId, base.baseId()));
    int historyAfterFirst = historyRows();
    SalesOrderEditResult again = saved(actorB, requestR(operationId, base.baseId()));

    assertThat(first.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(first.replayed()).isFalse();
    assertThat(first.resultVersion()).isEqualTo(version + 1);
    assertThat(first.nextBase().orderVersion()).isEqualTo(first.resultVersion());
    assertThat(first.lineIds())
        .singleElement()
        .extracting(SalesOrderEditLineIdMapping::clientLineId)
        .isEqualTo(cb);

    assertThat(again.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(again.replayed()).isTrue();
    assertThat(again.operationId()).isEqualTo(operationId);
    assertThat(again.lineIds()).isEqualTo(first.lineIds());
    assertThat(again.resultVersion()).isEqualTo(first.resultVersion());
    assertThat(again.nextBase().orderVersion()).isEqualTo(first.resultVersion());

    assertThat(activeLines()).isEqualTo(3);
    assertThat(linesWithClientId(cb)).isEqualTo(1);
    assertThat(receipts()).isEqualTo(1);
    assertThat(historyAfterFirst).isEqualTo(2);
    assertThat(historyRows()).isEqualTo(historyAfterFirst);
    assertThat(orderVersion()).isEqualTo(first.resultVersion());
  }

  @Test
  @DisplayName("S10.6: a request that never reached the server is applied when it is sent again")
  void requestThatNeverArrivedIsApplied() {
    SalesOrderEditBase base = open(actorB);
    UUID operationId = UUID.randomUUID();
    // The first attempt timed out on the way: nothing of it exists on the server.
    assertThat(receipts()).isZero();

    SalesOrderEditResult result = saved(actorB, requestR(operationId, base.baseId()));

    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(result.replayed()).isFalse();
    assertThat(activeLines()).isEqualTo(3);
    assertThat(linesWithClientId(cb)).isEqualTo(1);
    assertThat(receipts("APPLIED")).isEqualTo(1);
  }

  // ── S10.3: the same operation in two transactions ─────────────────────────

  @Test
  @DisplayName(
      "S10.3: the same request in two transactions at once: one APPLIED, the other replays;"
          + " one line, one receipt")
  void sameRequestTwiceAtOnce() throws Exception {
    SalesOrderEditBase base = open(actorB);
    long version = orderVersion();
    Map<String, Object> plain = requestR(UUID.randomUUID(), base.baseId());
    // Leases are always enforced (CEDIT-07-F3). One tab takes the keys before the order row is
    // locked and both workers send that tab's same proven request, so no acquire runs in the race:
    // the two lock waiters below are the two save transactions themselves. The first to apply
    // gives the leases back; the other is answered as a replay before any lease is checked.
    SalesOrderLeaseAutoProof.Held tab = holdForm(actorB, plain);
    Map<String, Object> request = tab.proven(plain);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    Object one;
    Object two;
    try (Connection holder = ownerConnection()) {
      holder.setAutoCommit(false);
      try {
        // Both saves queue on the order row, the first lock of every writer.
        lockOrderRow(holder, orderId);
        Future<Object> first = workers.submit(() -> save(actorB, request));
        Future<Object> second = workers.submit(() -> save(actorB, request));
        awaitLockWaiters(2, first, second);
        holder.rollback();
        one = join(first);
        two = join(second);
      } finally {
        holder.rollback();
      }
    } finally {
      workers.shutdownNow();
      closeForm(actorB, tab);
    }

    List<SalesOrderEditResult> results =
        List.of(asResult(one), asResult(two)).stream()
            .sorted(Comparator.comparing(SalesOrderEditResult::replayed))
            .toList();
    SalesOrderEditResult applied = results.get(0);
    SalesOrderEditResult replayed = results.get(1);
    assertThat(applied.replayed()).isFalse();
    assertThat(applied.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(replayed.replayed()).isTrue();
    assertThat(replayed.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(replayed.lineIds()).isEqualTo(applied.lineIds());
    assertThat(replayed.resultVersion()).isEqualTo(applied.resultVersion());
    assertThat(applied.resultVersion()).isEqualTo(version + 1);

    assertThat(activeLines()).isEqualTo(3);
    assertThat(linesWithClientId(cb)).isEqualTo(1);
    assertThat(receipts()).isEqualTo(1);
    assertThat(historyRows()).isEqualTo(2);
    assertThat(orderVersion()).isEqualTo(version + 1);
  }

  // ── S10.4: the same operation id with other content or another actor ─────

  @Test
  @DisplayName(
      "S10.4: the operation id with other content, or sent by another actor, is"
          + " 409 OPERATION_ID_REUSED and changes nothing")
  void operationIdWithOtherContentIsRefused() {
    SalesOrderEditBase base = open(actorB);
    UUID operationId = UUID.randomUUID();
    SalesOrderEditResult first = saved(actorB, requestR(operationId, base.baseId()));

    Map<String, Object> otherContent =
        withLines(
            body(operationId, base.baseId(), "notes", set("Later")),
            List.of(add(cb, p3, "quantity", set(quantity("300", "M")))));
    Object reusedByContent = save(actorB, otherContent);
    Object reusedByActor = save(actorA, requestR(operationId, base.baseId()));

    assertThat(failureCode(reusedByContent)).isEqualTo("OPERATION_ID_REUSED");
    assertThat(((DomainException) reusedByContent).getHttpStatus()).isEqualTo(409);
    assertThat(failureCode(reusedByActor)).isEqualTo("OPERATION_ID_REUSED");
    assertThat(orderVersion()).isEqualTo(first.resultVersion());
    assertThat(orderText("notes")).isEqualTo("Urgent");
    assertThat(activeLines()).isEqualTo(3);
    assertThat(receipts()).isEqualTo(1);
    assertThat(historyRows()).isEqualTo(2);
  }

  // ── S10.5: replay after the order moved on ────────────────────────────────

  @Test
  @DisplayName(
      "S10.5: R left v, C wrote v+1, R repeated: replayed with resultVersion v and nextBase at"
          + " v+1")
  void replayAfterAnotherWriterKeepsResultVersion() {
    SalesOrderEditBase base = open(actorB);
    UUID operationId = UUID.randomUUID();
    SalesOrderEditResult first = saved(actorB, requestR(operationId, base.baseId()));
    long v = first.resultVersion();

    SalesOrderEditBase baseC = open(actorC);
    SalesOrderEditResult byC =
        saved(actorC, body(UUID.randomUUID(), baseC.baseId(), "shippingMethod", set("Courier")));
    assertThat(byC.resultVersion()).isEqualTo(v + 1);

    SalesOrderEditResult again = saved(actorB, requestR(operationId, base.baseId()));

    assertThat(again.replayed()).isTrue();
    assertThat(again.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(again.resultVersion()).isEqualTo(v);
    assertThat(again.lineIds()).isEqualTo(first.lineIds());
    assertThat(again.nextBase().orderVersion()).isEqualTo(v + 1);
    assertThat(again.nextBase().order().getShippingMethod()).isEqualTo("Courier");
    assertThat(orderVersion()).isEqualTo(v + 1);
    assertThat(orderText("shipping_method")).isEqualTo("Courier");
    assertThat(activeLines()).isEqualTo(3);
    assertThat(receipts()).isEqualTo(2);
  }

  // ── S10.7: the same new line under a new operation id ────────────────────

  @Test
  @DisplayName(
      "S10.7: a new operation id with the same clientLineId ADD is 409 LINE_ALREADY_ADDED with"
          + " details.lineId")
  void sameClientLineUnderNewOperationIsRefused() {
    SalesOrderEditBase base = open(actorB);
    SalesOrderEditResult first = saved(actorB, requestR(UUID.randomUUID(), base.baseId()));
    UUID addedLine = first.lineIds().getFirst().lineId();
    long version = orderVersion();

    Object again =
        save(
            actorB,
            withLines(
                body(UUID.randomUUID(), first.nextBase().baseId()),
                List.of(add(cb, p3, "quantity", set(quantity("300", "M"))))));

    assertThat(failureCode(again)).isEqualTo("LINE_ALREADY_ADDED");
    DomainException failure = (DomainException) again;
    assertThat(failure.getHttpStatus()).isEqualTo(409);
    assertThat(failure.getDetails())
        .containsEntry("lineId", addedLine)
        .containsEntry("clientLineId", cb);
    assertThat(activeLines()).isEqualTo(3);
    assertThat(linesWithClientId(cb)).isEqualTo(1);
    assertThat(receipts()).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(version);
  }

  // ── S10.8: replay before the editable-state check ─────────────────────────

  @Test
  @DisplayName("S10.8: R repeated after the order went to planning still replays")
  void replayAfterSendingToPlanning() {
    SalesOrderEditBase base = open(actorB);
    UUID operationId = UUID.randomUUID();
    SalesOrderEditResult first = saved(actorB, requestR(operationId, base.baseId()));
    sendToPlanning();

    SalesOrderEditResult again = saved(actorB, requestR(operationId, base.baseId()));

    assertThat(again.replayed()).isTrue();
    assertThat(again.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(again.resultVersion()).isEqualTo(first.resultVersion());
    assertThat(again.lineIds()).isEqualTo(first.lineIds());
    assertThat(orderVersion()).isEqualTo(first.resultVersion());
    assertThat(activeLines()).isEqualTo(3);
    assertThat(receipts()).isEqualTo(1);
    assertThat(historyRows()).isEqualTo(2);
  }

  // ── S10.10: failure after the history rows, before commit ─────────────────

  @Test
  @DisplayName(
      "S10.10: a failure after the history rows were written and before commit leaves no line,"
          + " receipt, history, base or version change")
  void failureBeforeCommitRollsBackEverything() {
    SalesOrderEditBase base = open(actorB);
    long version = orderVersion();
    UUID operationId = UUID.randomUUID();
    Object failed;
    // A deferred constraint trigger fires at commit, after every history row is in place.
    jdbc.execute(
        "CREATE OR REPLACE FUNCTION sales_ord.cedit_test_fail_history() RETURNS trigger"
            + " LANGUAGE plpgsql AS $$ BEGIN IF NEW.tenant_id = '"
            + tenantId
            + "'::uuid THEN RAISE EXCEPTION 'injected failure before commit'; END IF;"
            + " RETURN NULL; END $$");
    try {
      jdbc.execute(
          "CREATE CONSTRAINT TRIGGER cedit_test_fail_history AFTER INSERT ON"
              + " sales_ord.order_field_change DEFERRABLE INITIALLY DEFERRED FOR EACH ROW"
              + " EXECUTE FUNCTION sales_ord.cedit_test_fail_history()");
      failed = save(actorB, requestR(operationId, base.baseId()));
    } finally {
      jdbc.execute(
          "DROP TRIGGER IF EXISTS cedit_test_fail_history ON sales_ord.order_field_change");
      jdbc.execute("DROP FUNCTION IF EXISTS sales_ord.cedit_test_fail_history()");
    }

    assertThat(failed)
        .isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(SalesOrderEditResult.class);
    assertThat((Throwable) failed).hasStackTraceContaining("injected failure before commit");
    assertThat(activeLines()).isEqualTo(2);
    assertThat(linesWithClientId(cb)).isZero();
    assertThat(receipts()).isZero();
    assertThat(historyRows()).isZero();
    assertThat(basesOfOrder(orderId, "SAVED")).isZero();
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(orderText("notes")).isNull();

    // Nothing of the failed attempt remains, and its locks are gone: the same request now applies.
    SalesOrderEditResult retried = saved(actorB, requestR(operationId, base.baseId()));
    assertThat(retried.replayed()).isFalse();
    assertThat(retried.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(retried.resultVersion()).isEqualTo(version + 1);
    assertThat(receipts()).isEqualTo(1);
  }

  // ── S10.11: history of a successful R ─────────────────────────────────────

  @Test
  @DisplayName(
      "S10.11: R writes history notes null->\"Urgent\" SET and LINE_ADDED for the new line, by B,"
          + " with the operation id and order_version = resultVersion")
  void historyOfSuccessfulRequest() {
    SalesOrderEditBase base = open(actorB);
    UUID operationId = UUID.randomUUID();

    SalesOrderEditResult result = saved(actorB, requestR(operationId, base.baseId()));

    UUID addedLine = result.lineIds().getFirst().lineId();
    UUID receiptId =
        jdbc.queryForObject(
            "SELECT id FROM sales_ord.order_edit_operation WHERE tenant_id = ?"
                + " AND operation_id = ?",
            UUID.class,
            tenantId,
            operationId);
    List<Map<String, Object>> rows = history();
    assertThat(rows).hasSize(2);
    Map<String, Object> lineAdded = rows.get(0);
    Map<String, Object> notes = rows.get(1);

    assertThat(lineAdded.get("edit_key")).isEqualTo("line");
    assertThat(lineAdded.get("change_kind")).isEqualTo("LINE_ADDED");
    assertThat(lineAdded.get("line_id")).isEqualTo(addedLine);
    assertThat(lineAdded.get("old_value")).isNull();
    assertThat((String) lineAdded.get("new_value")).isNotNull().contains(p3.toString());
    assertThat(lineAdded.get("resolution")).isNull();

    assertThat(notes.get("edit_key")).isEqualTo("notes");
    assertThat(notes.get("change_kind")).isEqualTo("SET");
    assertThat(notes.get("line_id")).isNull();
    assertThat(notes.get("old_value")).isNull();
    assertThat(notes.get("new_value")).isEqualTo("\"Urgent\"");
    assertThat(notes.get("resolution")).isNull();

    for (Map<String, Object> row : rows) {
      assertThat(row.get("actor_id")).isEqualTo(actorB.id());
      assertThat(row.get("operation_id")).isEqualTo(operationId);
      assertThat(((Number) row.get("order_version")).longValue()).isEqualTo(result.resultVersion());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.order_field_change WHERE sales_order_id = ?"
                    + " AND operation_receipt_id = ?",
                Integer.class,
                orderId,
                receiptId))
        .isEqualTo(2);
    assertThat(orderVersion()).isEqualTo(result.resultVersion());
  }

  // ── S10.12: a conflict whose answer was lost ──────────────────────────────

  @Test
  @DisplayName(
      "S10.12: a conflict repeated after a lost answer gives the same 409 and currentBase, with"
          + " one CONFLICT receipt")
  void lostConflictAnswerReplays() {
    SalesOrderEditBase baseA = open(actorA);
    SalesOrderEditBase baseB = open(actorB);
    saved(actorA, body(UUID.randomUUID(), baseA.baseId(), "paymentTerms", set("45 days")));
    long version = orderVersion();
    UUID operationId = UUID.randomUUID();
    Map<String, Object> request = body(operationId, baseB.baseId(), "paymentTerms", set("60 days"));

    Object firstFailure = save(actorB, request);
    Object secondFailure = save(actorB, request);
    Object thirdFailure = save(actorB, request);

    assertThat(firstFailure).isInstanceOf(SalesOrderEditConflictException.class);
    assertThat(secondFailure).isInstanceOf(SalesOrderEditConflictException.class);
    assertThat(failureCode(firstFailure)).isEqualTo("EDIT_CONFLICT");
    assertThat(failureCode(secondFailure)).isEqualTo("EDIT_CONFLICT");
    JsonNode first = ((SalesOrderEditConflictException) firstFailure).body();
    JsonNode second = ((SalesOrderEditConflictException) secondFailure).body();
    JsonNode third = ((SalesOrderEditConflictException) thirdFailure).body();

    assertThat(first.path("code").asText()).isEqualTo("EDIT_CONFLICT");
    assertThat(first.path("conflicts")).hasSize(1);
    JsonNode conflict = first.path("conflicts").get(0);
    assertThat(conflict.path("key").asText()).isEqualTo("paymentTerms");
    assertThat(conflict.path("reason").asText()).isEqualTo("CHANGED_ON_SERVER");
    assertThat(conflict.path("base").asText()).isEqualTo("30 days");
    assertThat(conflict.path("current").asText()).isEqualTo("45 days");
    assertThat(conflict.path("mine").asText()).isEqualTo("60 days");

    UUID currentBase = UUID.fromString(first.path("currentBase").path("baseId").asText());
    assertThat(second.path("currentBase").path("baseId").asText())
        .isEqualTo(currentBase.toString());
    assertThat(first.equals(SAME_VALUE, second))
        .as("the repeat answers the recorded problem: %s vs %s", first, second)
        .isTrue();
    assertThat(third).isEqualTo(second);

    assertThat(receipts("CONFLICT")).isEqualTo(1);
    assertThat(receipts()).isEqualTo(2);
    assertThat(basesOfOrder(orderId, "CONFLICT")).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(orderText("payment_terms")).isEqualTo("45 days");

    SalesOrderEditOperationView view =
        (SalesOrderEditOperationView)
            as(actorB, () -> edits.operation(orderId, operationId, actorB.id()));
    assertThat(view.outcome()).isEqualTo(SalesOrderEditOutcome.CONFLICT);
    assertThat(view.resultVersion()).isNull();
    assertThat(view.conflictBaseId()).isEqualTo(currentBase);
  }

  // ── one operation id on two orders ────────────────────────────────────────

  @Test
  @DisplayName(
      "S10.4: one operation id raced on two orders: exactly one applies, the other is 409"
          + " OPERATION_ID_REUSED through the unique constraint")
  void sameOperationIdRacedOnTwoOrders() throws Exception {
    UUID secondOrder = secondOrder();
    SalesOrderEditBase onFirst = open(actorB);
    SalesOrderEditBase onSecond = openOn(actorB, secondOrder);
    long firstVersion = versionOf(orderId);
    long secondVersion = versionOf(secondOrder);
    UUID operationId = UUID.randomUUID();
    Map<String, Object> toFirst = body(operationId, onFirst.baseId(), "notes", set("Urgent"));
    Map<String, Object> toSecond = body(operationId, onSecond.baseId(), "notes", set("Urgent"));

    ExecutorService workers = Executors.newFixedThreadPool(2);
    Object first;
    Object second;
    try (Connection holder = ownerConnection()) {
      holder.setAutoCommit(false);
      try {
        // An uncommitted receipt with the same id: both saves pass their receipt lookup and then
        // queue on the unique index, so both really reach the constraint.
        insertReceipt(holder, operationId, orderId, actorB.id());
        Future<Object> one = workers.submit(() -> saveOn(actorB, orderId, toFirst));
        Future<Object> two = workers.submit(() -> saveOn(actorB, secondOrder, toSecond));
        awaitLockWaiters(2, one, two);
        holder.rollback();
        first = join(one);
        second = join(two);
      } finally {
        holder.rollback();
      }
    } finally {
      workers.shutdownNow();
    }

    boolean firstWon = first instanceof SalesOrderEditResult;
    Object winner = firstWon ? first : second;
    Object loser = firstWon ? second : first;
    UUID winnerOrder = firstWon ? orderId : secondOrder;
    UUID loserOrder = firstWon ? secondOrder : orderId;
    long loserVersion = firstWon ? secondVersion : firstVersion;

    assertThat(winner).isInstanceOf(SalesOrderEditResult.class);
    assertThat(((SalesOrderEditResult) winner).outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(((SalesOrderEditResult) winner).replayed()).isFalse();
    assertThat(failureCode(loser)).isEqualTo("OPERATION_ID_REUSED");

    assertThat(
            jdbc.queryForList(
                "SELECT sales_order_id FROM sales_ord.order_edit_operation WHERE tenant_id = ?"
                    + " AND operation_id = ?",
                UUID.class,
                tenantId,
                operationId))
        .containsExactly(winnerOrder);
    assertThat(textOf(winnerOrder, "notes")).isEqualTo("Urgent");
    assertThat(textOf(loserOrder, "notes")).isNull();
    assertThat(versionOf(loserOrder)).isEqualTo(loserVersion);
    assertThat(basesOfOrder(loserOrder, "SAVED")).isZero();
    assertThat(historyOf(loserOrder)).isZero();
  }

  // ── S8.7: reading a receipt ───────────────────────────────────────────────

  @Test
  @DisplayName(
      "S8.7: the receipt is read back by its owner; another actor and another order get not"
          + " found")
  void receiptIsReadOnlyByItsOwnerOnItsOrder() {
    UUID secondOrder = secondOrder();
    SalesOrderEditBase base = open(actorB);
    UUID operationId = UUID.randomUUID();
    SalesOrderEditResult result = saved(actorB, requestR(operationId, base.baseId()));

    Object own = as(actorB, () -> edits.operation(orderId, operationId, actorB.id()));
    Object byAnother = as(actorA, () -> edits.operation(orderId, operationId, actorA.id()));
    Object onAnotherOrder =
        as(actorB, () -> edits.operation(secondOrder, operationId, actorB.id()));
    Object unknown = as(actorB, () -> edits.operation(orderId, UUID.randomUUID(), actorB.id()));

    assertThat(own).isInstanceOf(SalesOrderEditOperationView.class);
    SalesOrderEditOperationView view = (SalesOrderEditOperationView) own;
    assertThat(view.operationId()).isEqualTo(operationId);
    assertThat(view.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(view.resultVersion()).isEqualTo(result.resultVersion());
    assertThat(view.lineIds()).isEqualTo(result.lineIds());
    assertThat(view.conflictBaseId()).isNull();
    assertThat(view.recordedAt()).isNotNull();
    assertThat(byAnother).isInstanceOf(NotFoundException.class);
    assertThat(onAnotherOrder).isInstanceOf(NotFoundException.class);
    assertThat(unknown).isInstanceOf(NotFoundException.class);
    assertThat(receipts()).isEqualTo(1);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /** Request R with the given operation and base. */
  private Map<String, Object> requestR(UUID operationId, UUID baseId) {
    return withLines(
        body(operationId, baseId, "notes", set("Urgent")),
        List.of(add(cb, p3, "quantity", set(quantity("300", "M")))));
  }

  private static SalesOrderEditResult asResult(Object result) {
    if (result instanceof SalesOrderEditResult saved) {
      return saved;
    }
    throw new AssertionError("Expected a saved result but got " + result);
  }

  /** A second draft order of the same customer in this test's tenant. */
  private UUID secondOrder() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(actorA.id());
    try {
      SalesOrder order =
          SalesOrder.builder()
              .tradingPartnerId(partnerId)
              .orderNumber("SO-E2-" + UUID.randomUUID().toString().substring(0, 8))
              .status(OrderStatus.DRAFT)
              .orderDate(ORDER_DATE)
              .paymentTerms("30 days")
              .contactName("Jane Hill")
              .contactEmail("jane@example.com")
              .build();
      order.applyDeliveryTerms(
          DeliveryTerms.of(DeliveryTerm.FCA, "York", IncotermsVersion.INCOTERMS_2020));
      order.applyDeliveryTermStatus(DeliveryTermStatus.PROPOSED, null);
      return orders.saveAndFlush(order).getId();
    } finally {
      TenantContext.clear();
    }
  }

  private SalesOrderEditBase openOn(Actor actor, UUID order) {
    Object result = as(actor, () -> edits.openBase(order, actor.id(), actor.authentication()));
    if (result instanceof RuntimeException failure) {
      throw failure;
    }
    return (SalesOrderEditBase) result;
  }

  private long versionOf(UUID order) {
    return jdbc.queryForObject(
        "SELECT version FROM sales_ord.sales_order WHERE id = ?", Long.class, order);
  }

  private String textOf(UUID order, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM sales_ord.sales_order WHERE id = ?", String.class, order);
  }

  private int historyOf(UUID order) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.order_field_change WHERE sales_order_id = ?",
        Integer.class,
        order);
  }

  private int basesOfOrder(UUID order, String origin) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.order_edit_base WHERE sales_order_id = ? AND origin = ?",
        Integer.class,
        order,
        origin);
  }

  private int linesWithClientId(UUID clientLineId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.sales_order_line WHERE sales_order_id = ?"
            + " AND client_line_id = ?",
        Integer.class,
        orderId,
        clientLineId);
  }

  /** A connection of its own, outside the application's pool and transactions. */
  private static Connection ownerConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static void lockOrderRow(Connection holder, UUID order) throws SQLException {
    try (PreparedStatement lock =
        holder.prepareStatement("SELECT id FROM sales_ord.sales_order WHERE id = ? FOR UPDATE")) {
      lock.setObject(1, order);
      lock.executeQuery().close();
    }
  }

  /** An uncommitted receipt that holds the (tenant, operation id) unique key. */
  private void insertReceipt(Connection holder, UUID operationId, UUID order, UUID actor)
      throws SQLException {
    try (PreparedStatement insert =
        holder.prepareStatement(
            "INSERT INTO sales_ord.order_edit_operation (id, tenant_id, created_at, updated_at,"
                + " is_active, version, operation_id, sales_order_id, actor_id, base_id,"
                + " request_fingerprint, outcome, result_version, result_base_id, line_ids,"
                + " recorded_at) VALUES (?, ?, now(), now(), true, 0, ?, ?, ?, ?, ?,"
                + " 'NO_CHANGE', 0, ?, '[]'::jsonb, now())")) {
      insert.setObject(1, UUID.randomUUID());
      insert.setObject(2, tenantId);
      insert.setObject(3, operationId);
      insert.setObject(4, order);
      insert.setObject(5, actor);
      insert.setObject(6, UUID.randomUUID());
      insert.setString(7, "0".repeat(64));
      insert.setObject(8, UUID.randomUUID());
      insert.executeUpdate();
    }
  }

  /**
   * Waits until {@code count} backends wait for a lock, within a bound; a worker that finished
   * before it started waiting fails the test with what it returned.
   */
  private void awaitLockWaiters(int count, Future<?>... workers) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOUND_SECONDS);
    while (lockWaiters() < count) {
      for (Future<?> worker : workers) {
        if (worker.isDone()) {
          throw new AssertionError(
              "A save finished before it waited for the lock: " + join(worker));
        }
      }
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException(count + " transactions did not start waiting for a lock");
      }
      Thread.onSpinWait();
    }
  }

  private int lockWaiters() {
    Integer waiting =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                + " AND wait_event_type = 'Lock'",
            Integer.class);
    return waiting == null ? 0 : waiting;
  }

  /** A worker's result within the bound; its failure becomes the test's failure. */
  private static <T> T join(Future<T> worker) {
    try {
      return worker.get(BOUND_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException failure) {
      throw new AssertionError("A concurrent save failed", failure.getCause());
    } catch (TimeoutException timeout) {
      throw new AssertionError("A concurrent save did not finish in time", timeout);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }
}
