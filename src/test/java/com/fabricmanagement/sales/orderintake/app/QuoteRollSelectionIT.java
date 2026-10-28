package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.production.core.batch.api.BatchLotQuantityIntentPort;
import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.dto.*;
import com.fabricmanagement.sales.quote.dto.QuoteRollSelectionRequests;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuoteRollSelectionIT extends MetreOrderITSupport {
  @Autowired BatchLotQuantityIntentPort intents;
  @Autowired com.fabricmanagement.sales.lot.app.SalesLotService lots;
  @Autowired com.fabricmanagement.sales.quote.app.QuoteService quoteWriter;

  @Test
  void ownMetreIntentAndSeventeenProvisionalRollsConsumeStockOnceAndApplyAtomically() {
    in(
        tenant,
        () ->
            intents.replaceIntents(
                quoteId,
                "Q-NORTHBRIDGE",
                quoteLineId,
                actor,
                "Emma Whitfield",
                LocalDate.of(2027, 1, 1),
                List.of(
                    new BatchLotQuantityIntentPort.LotIntentRequest(
                        batch, new BigDecimal("2050"), "M"))));
    var session = quoteSession();
    // No first lease or user-supplied own-line exemption is needed to evaluate the saved target.
    assertThat(
            in(tenant, () -> service.preview(session.selectionId(), request(), actor))
                .lots()
                .getFirst()
                .free()
                .canonical())
        .isEqualByComparingTo("3000");
    var held = holdQuote(session);
    var otherView = in(tenant, () -> previews.preview(request(), otherActor));
    assertThat(otherView.lots().getFirst().free().canonical()).isEqualByComparingTo("875");
    var command =
        new QuoteRollSelectionRequests.Apply(UUID.randomUUID(), quoteVersion(), proof(held));
    var result = in(tenant, () -> quoteCommands.apply(quoteId, quoteLineId, command, actor));
    assertThat(result.commercialQuantity()).isEqualByComparingTo("2125");
    assertThat(result.customerRequestQuantity()).isEqualByComparingTo("2050");
    assertThat(result.heldPieceIds()).containsExactlyInAnyOrderElementsOf(rolls.subList(0, 17));
    assertThat(result.quoteVersion()).isEqualTo(command.expectedVersion() + 1);
    assertThat(in(tenant, () -> quoteCommands.apply(quoteId, quoteLineId, command, actor)))
        .isEqualTo(result);
    assertThat(in(tenant, () -> service.status(held.selectionId(), actor)).state())
        .isEqualTo(DraftStockSelectionDto.State.TRANSFERRED);
    var remove = new QuoteRollSelectionRequests.Remove(UUID.randomUUID(), result.quoteVersion());
    var released = in(tenant, () -> quoteCommands.remove(quoteId, quoteLineId, remove, actor));
    assertThat(released.commercialQuantity()).isEqualByComparingTo("2125");
    assertThat(released.heldPieceIds()).isEmpty();
  }

  @Test
  void savingANewQuoteLineTransfersTheProvisionalRollsOnceAndPreservesTheRequest() {
    in(
        tenant,
        () -> {
          var quote = quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, quoteId).orElseThrow();
          quote.getLines().clear();
          quotes.saveAndFlush(quote);
          return null;
        });
    var session =
        in(
            tenant,
            () ->
                service.open(
                    new DraftStockSelectionRequests.Open(
                        UUID.randomUUID(), UUID.randomUUID(), product, null, null, quoteId, null),
                    actor));
    var held = holdQuote(session);
    var line = new com.fabricmanagement.sales.quote.dto.AddQuoteLineRequest();
    line.setProductId(product);
    line.setRequestedQty(new BigDecimal("2125"));
    line.setUnit("M");
    line.setOfferedPrice(new BigDecimal("8.60"));
    line.setClientLineId(session.clientLineId());
    line.setStockSelection(proof(held));
    var command = new QuoteRollSelectionRequests.Create(UUID.randomUUID(), quoteVersion(), line);
    var saved = in(tenant, () -> quoteCommands.create(quoteId, command, actor));
    assertThat(saved.customerRequestQuantity()).isEqualByComparingTo("2050");
    assertThat(saved.commercialQuantity()).isEqualByComparingTo("2125");
    assertThat(saved.heldPieceIds()).hasSize(17);
    assertThat(in(tenant, () -> quoteCommands.create(quoteId, command, actor))).isEqualTo(saved);
    assertThat(
            in(
                tenant,
                () ->
                    quotes
                        .findByTenantIdAndIdAndIsActiveTrue(tenant, quoteId)
                        .orElseThrow()
                        .getLines()
                        .size()))
        .isEqualTo(1);
  }

  @Test
  void aPriceOnlyEchoPreservesTheOriginalRequestAndTheExistingMetreIntent() {
    var held = holdQuote(quoteSession());
    long version = quoteVersion();
    in(
        tenant,
        () ->
            quoteCommands.apply(
                quoteId,
                quoteLineId,
                new QuoteRollSelectionRequests.Apply(UUID.randomUUID(), version, proof(held)),
                actor));
    var update = new com.fabricmanagement.sales.quote.dto.UpdateQuoteLineRequest();
    update.setRequestedQty(new BigDecimal("2125"));
    update.setUnit("M");
    update.setOfferedPrice(new BigDecimal("8.70"));
    in(
        tenant,
        () -> {
          quoteWriter.updateQuoteLine(quoteId, quoteLineId, update, actor);
          return null;
        });
    var state = in(tenant, () -> planning.quote(quoteId, quoteLineId, actor));
    assertThat(state.customerRequestQuantity()).isEqualByComparingTo("2050");
    assertThat(state.commercialQuantity()).isEqualByComparingTo("2125");
    assertThat(state.heldPieceIds()).hasSize(17);
    assertThat(in(tenant, () -> intents.hasActiveIntents(quoteLineId))).isTrue();
    assertThat(
            in(tenant, () -> previews.preview(request(), otherActor))
                .lots()
                .getFirst()
                .free()
                .canonical())
        .isEqualByComparingTo("875");
  }

  @Test
  void quoteConversionTransfersNamedRollsAndKeepsTheOriginalRequestBeforeTheOrderInsert() {
    var held = holdQuote(quoteSession());
    long version = quoteVersion();
    in(
        tenant,
        () ->
            quoteCommands.apply(
                quoteId,
                quoteLineId,
                new QuoteRollSelectionRequests.Apply(UUID.randomUUID(), version, proof(held)),
                actor));
    in(
        tenant,
        () -> {
          var quote = quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, quoteId).orElseThrow();
          quote.setStatus(com.fabricmanagement.sales.quote.domain.QuoteStatus.APPROVED);
          quotes.saveAndFlush(quote);
          return null;
        });
    var request = metreOrder(null);
    request.setQuoteId(quoteId);
    request.getLines().getFirst().setRequestedQty(new BigDecimal("2125"));
    request.getLines().getFirst().setSourceQuoteLineId(quoteLineId);
    var saved = in(tenant, () -> orders.createOrder(request));
    var line = saved.getLines().getFirst();
    assertThat(line.getRequestedQty()).isEqualByComparingTo("2125");
    assertThat(line.getCustomerRequestQty()).isEqualByComparingTo("2050");
    assertThat(line.getInitialRequestedQty()).isEqualByComparingTo("2050");
    assertThat(in(tenant, () -> planning.order(saved.getId(), line.getId(), actor)).heldPieceIds())
        .containsExactlyInAnyOrderElementsOf(rolls.subList(0, 17));
  }

  @Test
  void namedRollMeasurementsCannotBeReplacedByAClientsMetreAmount() {
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        lots.resolveNewSelectionSnapshots(
                            List.of(
                                new com.fabricmanagement.sales.quote.dto
                                    .QuoteLineLotSelectionRequest(
                                    batch, rolls.subList(0, 4), new BigDecimal("1500"))))))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.SalesDomainException.class);
    assertThat(in(tenant, () -> intents.hasActiveIntents(quoteLineId))).isFalse();
  }

  @Test
  void endingAQuoteLinesIntentEndsItsProvisionalPeriodsInTheSameTransaction() {
    var held = holdQuote(quoteSession());
    in(
        tenant,
        () -> {
          intents.releaseIntents(quoteLineId);
          return null;
        });
    var released = in(tenant, () -> service.status(held.selectionId(), actor));
    assertThat(released.state()).isEqualTo(DraftStockSelectionDto.State.RELEASED);
    assertThat(released.grants()).isEmpty();
    var fresh = quoteSession();
    assertThat(
            in(tenant, () -> service.preview(fresh.selectionId(), request(), actor))
                .lots()
                .getFirst()
                .free()
                .canonical())
        .isEqualByComparingTo("3000");
  }

  @Test
  void exactPolicyRejectsInflatedApplyAndPreservesTheHeldCandidate() {
    in(
        tenant,
        () -> {
          var quote = quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, quoteId).orElseThrow();
          quote
              .getLines()
              .getFirst()
              .recordQuantityPolicy(
                  SalesQuantityPolicy.EXACT,
                  actor,
                  java.time.Instant.now(),
                  "Customer refuses extra metres");
          quotes.saveAndFlush(quote);
          return null;
        });
    var held = holdQuote(quoteSession());
    assertThat(held.capabilities())
        .anyMatch(
            action ->
                action.action() == DraftStockSelectionDto.Action.APPLY
                    && !action.allowed()
                    && action.reason() == DraftStockSelectionDto.Reason.EXACT_QUANTITY_REQUIRED);
    long version = quoteVersion();
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        quoteCommands.apply(
                            quoteId,
                            quoteLineId,
                            new QuoteRollSelectionRequests.Apply(
                                UUID.randomUUID(), version, proof(held)),
                            actor)))
        .isInstanceOf(OrderIntakeException.class);
    assertThat(in(tenant, () -> service.status(held.selectionId(), actor)).state())
        .isEqualTo(DraftStockSelectionDto.State.HELD);
    assertThat(quoteVersion()).isEqualTo(version);
  }

  @Test
  void competingSessionsForTheSameTargetCannotExemptOneAnothersProvisionalRolls() throws Exception {
    var first = quoteSession();
    var second =
        in(
            tenant,
            () ->
                service.open(
                    new DraftStockSelectionRequests.Open(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        product,
                        null,
                        null,
                        quoteId,
                        quoteLineId),
                    otherActor));
    var preview = in(tenant, () -> service.preview(first.selectionId(), request(), actor));
    var key =
        preview.evaluation().options().stream()
            .filter(
                value ->
                    value.placement()
                        == com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption
                            .OptionPlacement.MANUAL)
            .findFirst()
            .orElseThrow()
            .optionKey();
    var start = new java.util.concurrent.CyclicBarrier(2);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var outcomes = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
      for (var session : List.of(first, second)) {
        UUID user = session.selectionId().equals(first.selectionId()) ? actor : otherActor;
        outcomes.add(
            pool.submit(
                () -> {
                  start.await(10, java.util.concurrent.TimeUnit.SECONDS);
                  try {
                    in(
                        tenant,
                        () ->
                            service.acquire(
                                session.selectionId(),
                                new DraftStockSelectionRequests.Acquire(
                                    UUID.randomUUID(),
                                    session.selectionRevision(),
                                    request(),
                                    key,
                                    List.of()),
                                user));
                    return true;
                  } catch (OrderIntakeException conflict) {
                    return false;
                  }
                }));
      }
      assertThat(
              List.of(
                  outcomes.get(0).get(30, java.util.concurrent.TimeUnit.SECONDS),
                  outcomes.get(1).get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
  }

  @Test
  void anotherTabOfTheSameActorStillCompetesForTheSameTargetsRolls() {
    holdQuote(quoteSession());
    var otherTab = quoteSession();
    var view = in(tenant, () -> service.preview(otherTab.selectionId(), request(), actor));
    assertThat(view.lots().getFirst().free().canonical()).isEqualByComparingTo("875");
    assertThat(view.evaluation().options())
        .filteredOn(
            option ->
                option.placement()
                    == com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption
                        .OptionPlacement.MANUAL)
        .noneMatch(option -> option.apply().allowed());
  }

  @Test
  void anotherActorsSessionCannotBorrowTheTargetExemptionOrApplyItsProof() {
    var held = holdQuote(quoteSession());
    assertThatThrownBy(
            () -> in(tenant, () -> service.preview(held.selectionId(), request(), otherActor)))
        .isInstanceOf(OrderIntakeException.class);
    assertThatThrownBy(() -> in(otherTenant, () -> service.status(held.selectionId(), actor)))
        .isInstanceOf(OrderIntakeException.class);
  }
}
