package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalStock;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.orderintake.app.ProductCorrectionService;
import com.fabricmanagement.sales.orderintake.app.QuantityAcceptanceService;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceChannel;
import com.fabricmanagement.sales.orderintake.domain.QuantityProposal;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeRequests;
import com.fabricmanagement.sales.orderintake.dto.QuantityAcceptanceDto;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityProposalRepository;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * S17.5 and S17.6 with the real {@code QuantityAcceptance.record} on PostgreSQL, in both commit
 * orders (CEDIT-03 review R4). L1 (P1, 1000 M) has a proposal with one option: 1020 M from one
 * piece of one lot, more than requested, so the customer's acceptance is recorded.
 *
 * <p>The stock read by the recording is a mock that offers the same eligible piece for any product:
 * that a corrected line is refused must come from the proposal's bond to its product, not from the
 * stock of the new product happening to lack the piece. Each writer runs on its own thread,
 * connection and transaction in its actor's tenant; the first one holds its locks at a pause point
 * until the second is seen waiting for a lock in {@code pg_stat_activity}. Waits are bounded and no
 * result may be a deadlock. A mock bean makes this class its own Spring context.
 */
class QuantityAcceptanceRecordRaceIT extends SalesOrderEditItSupport {

  private static final String DEADLOCK = "40P01";

  @MockitoBean private ProposalStockQueryService stock;

  @Autowired private QuantityAcceptanceService quantityAcceptances;
  @Autowired private ProductCorrectionService productCorrections;
  @Autowired private QuantityProposalRepository proposals;

  private final UUID lot = UUID.randomUUID();
  private final UUID piece = UUID.randomUUID();
  private UUID p9;
  private UUID proposalId;
  private QuantityOption above;
  private ExecutorService pool;
  private final List<Hold> holds = new ArrayList<>();

  /** Pauses the next stock read: a recording then holds its order and line locks. */
  private final AtomicReference<Hold> stockHold = new AtomicReference<>();

  /** Pauses the next catalogue lookup: a safe save then holds its order and line locks. */
  private final AtomicReference<Hold> catalogueHold = new AtomicReference<>();

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
  void stockAndProposal() {
    p9 = UUID.randomUUID();
    pool = Executors.newFixedThreadPool(4);
    stockHold.set(null);
    catalogueHold.set(null);
    doAnswer(
            invocation -> {
              Hold hold = stockHold.getAndSet(null);
              if (hold != null) {
                hold.pause();
              }
              return new ProposalStock(
                  List.of(
                      new ProposalLot(
                          lot,
                          "LOT-1",
                          null,
                          PrimaryMeasure.LENGTH,
                          "M",
                          WidthEvidence.NOT_REQUIRED,
                          List.of(
                              new ProposalPiece(
                                  piece,
                                  "R1",
                                  new BigDecimal("1020"),
                                  PieceState.ELIGIBLE,
                                  null,
                                  1L)))),
                  List.of());
            })
        .when(stock)
        .find(any());
    when(stock.measureFor(any())).thenReturn(Optional.of(PrimaryMeasure.LENGTH));
    doAnswer(
            invocation -> {
              Hold hold = catalogueHold.getAndSet(null);
              if (hold != null) {
                hold.pause();
              }
              UUID productId = invocation.getArgument(1);
              return productId == null ? Optional.empty() : Optional.of(definition(productId));
            })
        .when(productDefinitions)
        .find(any(), any());

    above =
        new QuantityOption(
            "ABOVE:0123456789abcdef",
            QuantityOption.OptionKind.ABOVE,
            QuantityOption.Compatibility.SINGLE_LOT,
            new BigDecimal("1020"),
            new BigDecimal("1020"),
            new BigDecimal("20"),
            new BigDecimal("2"),
            false,
            List.of(
                new QuantityOption.LotPart(
                    lot,
                    "LOT-1",
                    List.of(piece),
                    new BigDecimal("1020"),
                    0,
                    0,
                    false,
                    null,
                    null)));
    Object saved =
        as(
            actorA,
            () ->
                transactions.execute(
                    status ->
                        proposals
                            .save(
                                QuantityProposal.record(
                                    orderId,
                                    l1,
                                    p1,
                                    new BigDecimal("1000"),
                                    "M",
                                    new QuantityEvaluationResult(
                                        QuantityEvaluationResult.EvaluationStatus.OPTIONS,
                                        new BigDecimal("1000"),
                                        "M",
                                        "M",
                                        List.of(above),
                                        1,
                                        0,
                                        0,
                                        List.of(),
                                        false,
                                        0),
                                    "ab".repeat(32),
                                    actorA.id(),
                                    Instant.now()))
                            .getId()));
    if (saved instanceof RuntimeException failure) {
      throw failure;
    }
    proposalId = (UUID) saved;
  }

  @AfterEach
  void stopWorkers() throws InterruptedException {
    holds.forEach(Hold::release);
    stockHold.set(null);
    catalogueHold.set(null);
    pool.shutdownNow();
    pool.awaitTermination(20, TimeUnit.SECONDS);
  }

  // ── control ───────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Control: alone, the recording takes the option; L1 becomes 1020 M, version +1")
  void recordingAloneTakesTheOption() {
    long before = orderVersion();

    Object result = record(actorA);

    assertThat(result).isInstanceOf(QuantityAcceptanceDto.class);
    assertThat(lineDecimal(l1, "requested_qty")).isEqualByComparingTo("1020");
    assertThat(acceptances("ACTIVE")).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(before + 1);
  }

  // ── S17.5: product correction ↔ recording ─────────────────────────────────

  @Test
  @DisplayName(
      "S17.5 (correction first): the recording waits at the root, then refuses the old product's"
          + " proposal; no acceptance, L1 stays 1000 M on P9")
  void correctionFirstThenRecording() {
    long l1Before = lineVersion(l1);
    long before = orderVersion();
    Hold pcHolds = newHold();

    // The correction runs inside the held transaction, in its actor's tenant until it commits.
    CompletableFuture<Object> pc =
        async(
            () ->
                as(
                    actorA,
                    () ->
                        holding(
                            pcHolds,
                            () ->
                                productCorrections.correct(
                                    orderId, correction(l1Before), actorA.id()))));
    pcHolds.awaitReached();
    CompletableFuture<Object> recording = async(() -> record(actorB));
    awaitWaiting(recording);
    pcHolds.release();

    assertThat(result(pc)).isInstanceOf(List.class);
    Object refused = result(recording);
    assertThat(failureCode(refused)).isEqualTo("ORDER_INTAKE_PROPOSAL_STALE");
    assertThat(((DomainException) refused).getHttpStatus()).isEqualTo(409);
    assertThat(acceptances(null)).isZero();
    assertThat(lineProduct(l1)).isEqualTo(p9);
    assertThat(lineDecimal(l1, "requested_qty")).isEqualByComparingTo("1000");
    assertThat(orderVersion()).isEqualTo(before + 1);
  }

  @Test
  @DisplayName(
      "S17.5 (recording first): the correction waits at the root, then fails"
          + " ORDER_INTAKE_STALE_VERSION; the acceptance and P1 stay")
  void recordingFirstThenCorrection() {
    long l1Before = lineVersion(l1);
    long before = orderVersion();
    Hold recordingHolds = newHold();

    stockHold.set(recordingHolds);
    CompletableFuture<Object> recording = async(() -> record(actorB));
    recordingHolds.awaitReached();
    CompletableFuture<Object> pc = async(() -> correct(actorA, l1Before));
    awaitWaiting(pc);
    recordingHolds.release();

    assertThat(result(recording)).isInstanceOf(QuantityAcceptanceDto.class);
    Object refused = result(pc);
    assertThat(failureCode(refused)).isEqualTo("ORDER_INTAKE_STALE_VERSION");
    assertThat(acceptances("ACTIVE")).isEqualTo(1);
    assertThat(lineProduct(l1)).isEqualTo(p1);
    assertThat(lineDecimal(l1, "requested_qty")).isEqualByComparingTo("1020");
    assertThat(orderVersion()).isEqualTo(before + 1);
  }

  // ── S17.6: recording ↔ safe save of L1's quantity ─────────────────────────

  @Test
  @DisplayName(
      "S17.6 (recording first): the save waits at the root, then is CHANGED_ON_SERVER for"
          + " line.quantity (base 1000, current 1020, mine 1100)")
  void recordingFirstThenQuantitySave() {
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    Hold recordingHolds = newHold();

    stockHold.set(recordingHolds);
    CompletableFuture<Object> recording = async(() -> record(actorA));
    recordingHolds.awaitReached();
    CompletableFuture<Object> save =
        async(
            () ->
                save(
                    actorB,
                    withLines(
                        body(UUID.randomUUID(), base.baseId()),
                        List.of(update(l1, "quantity", set(quantity("1100", "M")))))));
    awaitWaiting(save);
    recordingHolds.release();

    assertThat(result(recording)).isInstanceOf(QuantityAcceptanceDto.class);
    Object answer = result(save);
    assertThat(answer).isInstanceOf(SalesOrderEditConflictException.class);
    JsonNode conflict = ((SalesOrderEditConflictException) answer).body().path("conflicts").get(0);
    assertThat(conflict.path("key").asText()).isEqualTo("line.quantity");
    assertThat(conflict.path("reason").asText()).isEqualTo("CHANGED_ON_SERVER");
    assertThat(new BigDecimal(conflict.path("base").path("requestedQty").asText()))
        .isEqualByComparingTo("1000");
    assertThat(new BigDecimal(conflict.path("current").path("requestedQty").asText()))
        .isEqualByComparingTo("1020");
    assertThat(new BigDecimal(conflict.path("mine").path("requestedQty").asText()))
        .isEqualByComparingTo("1100");
    assertThat(lineDecimal(l1, "requested_qty")).isEqualByComparingTo("1020");
    assertThat(acceptances("ACTIVE")).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(before + 1);
    assertThat(historyRows()).isZero();
  }

  @Test
  @DisplayName(
      "S17.6 (save first): the recording waits at the root and decides on the refreshed 1100 M:"
          + " its 1000 M proposal is stale, nothing is recorded")
  void quantitySaveFirstThenRecording() {
    SalesOrderEditBase base = open(actorB);
    long before = orderVersion();
    Hold saveHolds = newHold();

    catalogueHold.set(saveHolds);
    CompletableFuture<Object> save =
        async(
            () ->
                save(
                    actorB,
                    withLines(
                        body(UUID.randomUUID(), base.baseId()),
                        List.of(update(l1, "quantity", set(quantity("1100", "M")))))));
    saveHolds.awaitReached();
    CompletableFuture<Object> recording = async(() -> record(actorA));
    awaitWaiting(recording);
    saveHolds.release();

    Object saved = result(save);
    assertThat(saved).isInstanceOf(SalesOrderEditResult.class);
    assertThat(((SalesOrderEditResult) saved).outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(((SalesOrderEditResult) saved).resultVersion()).isEqualTo(before + 1);
    Object refused = result(recording);
    assertThat(failureCode(refused)).isEqualTo("ORDER_INTAKE_PROPOSAL_STALE");
    assertThat(acceptances(null)).isZero();
    assertThat(lineDecimal(l1, "requested_qty")).isEqualByComparingTo("1100");
    assertThat(orderVersion()).isEqualTo(before + 1);
  }

  // ── writers ───────────────────────────────────────────────────────────────

  private Object record(Actor actor) {
    return as(
        actor,
        () ->
            quantityAcceptances.record(
                orderId,
                l1,
                new OrderIntakeRequests.RecordQuantityAcceptance(
                    proposalId,
                    above.optionKey(),
                    false,
                    null,
                    "Jane Hill (buyer)",
                    AcceptanceChannel.PHONE,
                    Instant.now(),
                    null,
                    null,
                    true,
                    "record-" + UUID.randomUUID()),
                actor.id()));
  }

  private Object correct(Actor actor, long l1Version) {
    return as(actor, () -> productCorrections.correct(orderId, correction(l1Version), actor.id()));
  }

  private FulfilmentDtos.CorrectProduct correction(long l1Version) {
    return new FulfilmentDtos.CorrectProduct(
        p1, p9, List.of(new FulfilmentDtos.LineVersion(l1, l1Version)), "Wrong article chosen");
  }

  /** Runs a step in a transaction that stays open, with the locks it took, until released. */
  private <T> T holding(Hold hold, Supplier<T> step) {
    return transactions.execute(
        status -> {
          T result = step.get();
          hold.pause();
          return result;
        });
  }

  private Hold newHold() {
    Hold hold = new Hold();
    holds.add(hold);
    return hold;
  }

  private CompletableFuture<Object> async(Supplier<Object> step) {
    return CompletableFuture.supplyAsync(step, pool);
  }

  // ── waiting and results ───────────────────────────────────────────────────

  /** Polls until a backend waits for a lock, within a bound; fails if the step already ended. */
  private void awaitWaiting(CompletableFuture<Object> waiter) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (!someoneWaitsForALock()) {
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

  /** A worker's result within the bound; a worker error fails the test; never a deadlock. */
  private static Object result(CompletableFuture<Object> future) {
    Object result;
    try {
      result = future.get(20, TimeUnit.SECONDS);
    } catch (ExecutionException failure) {
      throw new AssertionError("The worker failed", failure.getCause());
    } catch (TimeoutException failure) {
      throw new AssertionError("The worker did not finish within 20 s", failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
    for (Throwable cause = result instanceof Throwable thrown ? thrown : null;
        cause != null;
        cause = cause.getCause()) {
      if ((cause instanceof SQLException sql && DEADLOCK.equals(sql.getSQLState()))
          || (cause.getMessage() != null && cause.getMessage().contains("deadlock detected"))) {
        throw new AssertionError("A transaction ended in a deadlock (40P01)", cause);
      }
    }
    return result;
  }

  // ── reads and fixtures ────────────────────────────────────────────────────

  private int acceptances(String status) {
    return status == null
        ? jdbc.queryForObject(
            "SELECT count(*) FROM sales_ord.quantity_acceptance WHERE sales_order_line_id = ?",
            Integer.class,
            l1)
        : jdbc.queryForObject(
            "SELECT count(*) FROM sales_ord.quantity_acceptance"
                + " WHERE sales_order_line_id = ? AND status = ?",
            Integer.class,
            l1,
            status);
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
}
