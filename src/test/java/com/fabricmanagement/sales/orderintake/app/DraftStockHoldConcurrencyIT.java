package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitHeldException;
import com.fabricmanagement.sales.orderintake.dto.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DraftStockHoldConcurrencyIT extends DraftStockSelectionITSupport {
  @Autowired StockUnitSoftHoldPort quoteHolds;
  @Autowired com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort allocations;

  @Test
  void twoMarketersCannotAcquireOverlappingDisplayedProposals() throws Exception {
    var first = open(actor);
    var second = open(otherActor);
    var a = command(first, UUID.randomUUID());
    var b = command(second, UUID.randomUUID());
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var futures =
          List.of(
              workers.submit(() -> race(first, a, actor, ready, start)),
              workers.submit(() -> race(second, b, otherActor, ready, start)));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              List.of(
                  futures.get(0).get(20, TimeUnit.SECONDS),
                  futures.get(1).get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
  }

  @Test
  void samePersonsSecondTabIsAnotherOwnerAndQuoteCannotTakeItsRolls() {
    var first = acquire(open(actor), actor);
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () -> {
                      quoteHolds.replaceHolds(
                          UUID.randomUUID(),
                          first.grants().stream()
                              .map(DraftStockSelectionDto.Grant::stockUnitId)
                              .toList());
                      return null;
                    }))
        .isInstanceOf(StockUnitHeldException.class);
    var second = open(actor);
    assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
    var status = in(tenant, () -> service.status(first.selectionId(), actor));
    assertThat(status.grants()).hasSize(7);
  }

  @Test
  void quoteWriterAndProvisionalAcquisitionUseTheSameDatabaseGate() throws Exception {
    var session = open(actor);
    var command = command(session, UUID.randomUUID());
    var ids =
        in(tenant, () -> previews.preview(request(), actor))
            .evaluation()
            .options()
            .getFirst()
            .lots()
            .stream()
            .flatMap(lot -> lot.pieceIds().stream())
            .toList();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var pending = workers.submit(() -> race(session, command, actor, ready, start));
      var quote =
          workers.submit(
              () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("Race did not start");
                try {
                  in(
                      tenant,
                      () -> {
                        quoteHolds.replaceHolds(UUID.randomUUID(), ids);
                        return null;
                      });
                  return true;
                } catch (StockUnitHeldException blocked) {
                  return false;
                }
              });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(pending.get(20, TimeUnit.SECONDS), quote.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
  }

  @Test
  void hardAllocationAndDisplayedProposalCannotBothOwnTheSameRolls() throws Exception {
    var session = open(actor);
    var command = command(session, UUID.randomUUID());
    var preview = in(tenant, () -> previews.preview(request(), actor));
    var refs =
        preview.evaluation().options().get(preview.evaluation().mainOptionIndex()).lots().stream()
            .flatMap(lot -> lot.pieceIds().stream())
            .map(
                id ->
                    new com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort
                        .PieceRef(id, batch, 0L))
            .toList();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var pending = workers.submit(() -> race(session, command, actor, ready, start));
      var hard =
          workers.submit(
              () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("Race did not start");
                try {
                  return in(
                      tenant,
                      () ->
                          allocations
                              .allocate(
                                  new com.fabricmanagement.production.core.stockunit.api
                                      .PieceAllocationPort.AllocationRequest(
                                      tenant,
                                      UUID.randomUUID(),
                                      UUID.randomUUID(),
                                      otherActor,
                                      refs))
                              .allocated());
                } catch (StockUnitHeldException blocked) {
                  return false;
                }
              });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(pending.get(20, TimeUnit.SECONDS), hard.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
  }

  private boolean race(
      DraftStockSelectionDto session,
      DraftStockSelectionRequests.Acquire command,
      UUID user,
      CountDownLatch ready,
      CountDownLatch start)
      throws Exception {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
    try {
      in(tenant, () -> service.acquire(session.selectionId(), command, user));
      return true;
    } catch (com.fabricmanagement.sales.common.exception.OrderIntakeException conflict) {
      return false;
    }
  }
}
