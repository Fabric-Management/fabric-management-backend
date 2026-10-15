package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.inventory.lothealth.domain.LotHealthPolicy;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalReason;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult.EvaluationStatus;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.BlockedReason;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.Compatibility;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionPlacement;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.StockSource;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.ToleranceStatus;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * STOCK-PROPOSAL-2 and SOI S04, S05, S09, A04 on the evaluation level (Fatih, 2026-10-12 to
 * 2026-10-14): no computed option leaves a lot fewer normal pieces than the minimum, short pieces
 * not counting and going first; the proposal opens at the best free option meeting the request
 * within the tolerance (a remnant lot closed, more lots closed, the smaller lot completed from,
 * older stock, fewer lots, closeness), else the nearest above, else the best below, else held
 * stock; beside it one card of each kind: the best below, the nearest above outside the tolerance,
 * pending combinations; no card far below the request only because it closes a lot; quantity order
 * with the opening index; held stock only when free stock cannot reach the lower tolerance limit;
 * the manual pick. Data is fictional.
 */
class QuantityEvaluatorTest {

  private static final Instant OLD = Instant.parse("2025-03-01T00:00:00Z");
  private static final Instant NEW = Instant.parse("2026-06-01T00:00:00Z");

  private final UUID p1 = new UUID(0, 1);
  private final UUID p2 = new UUID(0, 2);
  private final UUID p3 = new UUID(0, 3);

  // ── No computed option leaves one or two pieces (Fatih, 2026-10-13) ────────

  @Test
  void pieces120_110_90For200_everyPartialTakeLeavesOneOrTwoPieces_soTheWholeLotOpens() {
    var result = evaluate(spec("200").lots(lot(p1, "P1", null, 120, 110, 90)).tolerance("5", "5"));

    // 110 + 90 would leave the 120 m piece, 120 alone would leave two: neither is offered.
    QuantityOption opening = main(result);
    assertThat(opening.quantity()).isEqualByComparingTo("320");
    assertThat(opening.lots().getFirst().closesLot()).isTrue();
    assertThat(opening.leavesRemnant()).isFalse();
    assertThat(opening.toleranceStatus()).isEqualTo(ToleranceStatus.OUTSIDE);
    assertThat(quantities(result)).containsExactly("320");
  }

  @Test
  void aLotOfFourMayKeepThreePieces_neverTwo() {
    var result =
        evaluate(spec("100").lots(lot(p1, "P1", null, 100, 100, 100, 100)).tolerance("0", "0"));

    assertThat(main(result).quantity()).isEqualByComparingTo("100");
    assertThat(main(result).lots().getFirst().remainingEligiblePieces()).isEqualTo(3);
    assertThat(result.options())
        .noneMatch(option -> option.quantity().compareTo(new BigDecimal("200")) == 0);
  }

  // ── Where the proposal opens (Fatih, 2026-10-13) ─────────────────────────

  @Test
  void theBestMeetingTheRequestWithinTolerance_opens_closingALotBeforeFewerLotsAndCloseness() {
    UUID old = new UUID(0, 31);
    UUID fresh = new UUID(0, 32);
    var result =
        evaluate(
            spec("250")
                .lots(
                    lot(old, "OLD", OLD, 100, 100),
                    lot(fresh, "NEW", NEW, 100, 100, 100, 100, 100, 100))
                .confirmed(Set.of(old, fresh))
                .tolerance("20", "20"));

    // 300 m either way: the old lot closed plus one new piece beats three new pieces.
    QuantityOption opening = main(result);
    assertThat(opening.quantity()).isEqualByComparingTo("300");
    assertThat(opening.toleranceStatus()).isEqualTo(ToleranceStatus.WITHIN);
    assertThat(part(opening, old).closesLot()).isTrue();
    assertThat(part(opening, fresh).remainingEligiblePieces()).isEqualTo(5);
  }

  @Test
  void withNothingWithinTolerance_theNearestAboveOpens_andTheBestBelowStaysListed() {
    var result =
        evaluate(
            spec("450")
                .lots(lot(p1, "P1", null, 100, 100, 100, 100, 100, 100))
                .tolerance("5", "5"));

    // Takes leave at least three pieces: 100, 200, 300 or the whole 600 m.
    QuantityOption opening = main(result);
    assertThat(opening.quantity()).isEqualByComparingTo("600");
    assertThat(opening.placement()).isEqualTo(OptionPlacement.MAIN);
    assertThat(opening.toleranceStatus()).isEqualTo(ToleranceStatus.OUTSIDE);
    QuantityOption below = byQuantity(result, "300");
    assertThat(below.placement()).isEqualTo(OptionPlacement.BELOW);
  }

  @Test
  void withNothingAtOrAboveTheRequest_theBestBelowOpens() {
    var result =
        evaluate(spec("700").lots(lot(p1, "P1", OLD, 100, 100, 100, 95)).tolerance("5", "5"));

    QuantityOption opening = main(result);
    assertThat(opening.quantity()).isEqualByComparingTo("395");
    assertThat(opening.difference()).isEqualByComparingTo("-305");
    assertThat(opening.lots().getFirst().closesLot()).isTrue();
  }

  @Test
  void aLotFarBelowTheRequest_isNotACard_evenThoughClosingItWouldEmptyIt() {
    var result =
        evaluate(
            spec("700")
                .lots(
                    lot(p1, "X", OLD, 100, 100, 100),
                    lot(p2, "Y", NEW, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100))
                .tolerance("5", "5"));

    // X is older and would close, but it cannot be combined with Y and is 400 m short (Fatih,
    // 2026-10-14): the short card is the best below, Y's 600 m, and X is no card of its own.
    assertThat(main(result).quantity()).isEqualByComparingTo("700");
    assertThat(main(result).batchIds()).containsExactly(p2);
    assertThat(main(result).lots().getFirst().remainingEligiblePieces()).isEqualTo(3);
    assertThat(quantities(result)).containsExactly("700", "600");
    assertThat(byQuantity(result, "600").batchIds()).containsExactly(p2);
  }

  @Test
  void lot23087_theNewLotMeetsTheRequest_closingTheOldLotIsTheBestBelow() {
    UUID lot23087 = new UUID(0, 23087);
    UUID newer = new UUID(0, 99);
    var result =
        evaluate(
            spec("700")
                .lots(
                    lot(lot23087, "LOT-23087", OLD, 95, 95, 100),
                    lot(newer, "LOT-NEW", NEW, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100))
                .confirmed(Set.of(lot23087, newer))
                .tolerance("5", "5"));

    // The old lot is closed or untouched: one or two of its pieces would leave two or one.
    QuantityOption opening = main(result);
    assertThat(opening.quantity()).isEqualByComparingTo("700");
    assertThat(opening.batchIds()).containsExactly(newer);
    assertThat(opening.lots().getFirst().remainingEligiblePieces()).isEqualTo(3);
    QuantityOption below = byQuantity(result, "690");
    assertThat(below.placement()).isEqualTo(OptionPlacement.BELOW);
    assertThat(below.toleranceStatus()).isEqualTo(ToleranceStatus.WITHIN);
    assertThat(part(below, lot23087).closesLot()).isTrue();
    assertThat(part(below, newer).quantity()).isEqualByComparingTo("400");
    assertThat(below.reasons())
        .extracting(ProposalReason::code)
        .contains(ProposalReason.Code.CLOSES_LOTS);
  }

  @Test
  void aManualPickMayLeaveTwoPieces_onlyWithAcknowledgement() {
    UUID a = new UUID(4, 1);
    List<ProposalPiece> rest = both(pieces(new UUID(4, 2), 60), pieces(new UUID(4, 3), 50));
    var result =
        evaluate(
            spec("100")
                .lots(lot(p1, "P1", null, both(pieces(a, 100), rest)))
                .tolerance("0", "0")
                .manual(a));

    QuantityOption manual =
        result.options().stream()
            .filter(option -> option.placement() == OptionPlacement.MANUAL)
            .findFirst()
            .orElseThrow();
    assertThat(manual.leavesRemnant()).isTrue();
    assertThat(manual.needsRemnantAcknowledgement()).isTrue();
    assertThat(manual.blockedReason()).isNull();
    assertThat(manual.lots().getFirst().remnantPieceIds()).hasSize(2);
    assertThat(manual.reasons())
        .filteredOn(reason -> reason.code() == ProposalReason.Code.LEAVES_REMNANT)
        .singleElement()
        .satisfies(
            reason -> {
              assertThat(reason.count()).isEqualTo(2);
              assertThat(reason.quantity()).isEqualByComparingTo("110");
            });
    // The computed option closes the lot instead.
    assertThat(main(result).quantity()).isEqualByComparingTo("210");
  }

  @Test
  void severalFittingLotsAreTakenAgainstTheRemainingTarget_neverEachAgainstTheRequest() {
    UUID a = new UUID(0, 11);
    UUID b = new UUID(0, 12);
    UUID c = new UUID(0, 13);
    var result =
        evaluate(
            spec("450")
                .lots(
                    lot(a, "A", OLD, 100, 100, 100),
                    lot(b, "B", Instant.parse("2025-06-01T00:00:00Z"), 150),
                    lot(c, "C", NEW, 100))
                .confirmed(Set.of(a, b, c)));

    QuantityOption main = main(result);
    // 300 fits 450, then 150 fits the 150 left; 100 m of C would overshoot what remains.
    assertThat(main.quantity()).isEqualByComparingTo("450");
    assertThat(main.batchIds()).containsExactlyInAnyOrder(a, b);
    assertThat(main.lots()).allSatisfy(part -> assertThat(part.closesLot()).isTrue());
    assertThat(result.status()).isEqualTo(EvaluationStatus.EXACT);
  }

  @Test
  void s04_closingBothConfirmedLotsOpens_aSingleLotIsTheNearestBelow() {
    var result =
        evaluate(
            spec("500")
                .lots(lot(p1, "P1", null, 100, 100, 100), lot(p2, "P2", null, 100, 100, 100))
                .confirmed(Set.of(p1, p2))
                .tolerance("20", "20"));

    // 400 and 500 m would leave two or one piece in a lot; they are not offered.
    assertThat(quantities(result)).containsExactly("600", "300");
    assertThat(main(result).quantity()).isEqualByComparingTo("600");
    assertThat(main(result).compatibility()).isEqualTo(Compatibility.CONFIRMED);
    assertThat(main(result).lots()).allSatisfy(part -> assertThat(part.closesLot()).isTrue());
    assertThat(byQuantity(result, "300").placement()).isEqualTo(OptionPlacement.BELOW);
    assertThat(result.mainOptionIndex()).isZero();
  }

  // ── Card order and the main index (Fatih, 2026-10-14) ─────────────────────

  @Test
  void theOpeningComesFirst_thenTheOtherCardsInTheDecidedOrder_notByQuantity() {
    var result =
        evaluate(
            spec("450")
                .lots(lot(p1, "P1", null, 100, 100, 100, 100, 100, 100))
                .tolerance("5", "5"));

    // The opening closes the lot at 600 m; the best below keeps three pieces at 300 m.
    assertThat(result.mainOptionIndex()).isZero();
    assertThat(quantities(result)).containsExactly("600", "300");
    assertThat(result.options())
        .extracting(QuantityOption::placement)
        .containsExactly(OptionPlacement.MAIN, OptionPlacement.BELOW);
  }

  @Test
  void changedPiecesComeFirst_andTheOpeningRightAfter() {
    ProposalLot six = lot(p1, "P1", null, 100, 100, 100, 100, 100, 100);
    UUID[] two =
        six.pieces().subList(0, 2).stream().map(ProposalPiece::stockUnitId).toArray(UUID[]::new);

    var result = evaluate(spec("450").lots(six).tolerance("5", "5").manual(two));

    assertThat(result.options())
        .extracting(QuantityOption::placement)
        .containsExactly(OptionPlacement.MANUAL, OptionPlacement.MAIN, OptionPlacement.BELOW);
    assertThat(quantities(result)).containsExactly("200", "600", "300");
    assertThat(result.mainOptionIndex()).isEqualTo(1);
  }

  @Test
  void theSameStockInAnotherOrderGivesTheSameOptions() {
    ProposalLot first = lot(p1, "P1", null, 100, 100, 50);
    ProposalLot second = lot(p2, "P2", null, 100, 60);
    var forward = evaluate(spec("210").lots(first, second).confirmed(Set.of(p1, p2)));
    var backward = evaluate(spec("210").lots(second, first).confirmed(Set.of(p1, p2)));

    assertThat(backward.options()).isEqualTo(forward.options());
    assertThat(backward.mainOptionIndex()).isEqualTo(forward.mainOptionIndex());
  }

  // ── Age: known dates only ────────────────────────────────────────────────

  @Test
  void olderStockIsPreferredAndSaid_onlyWhenEveryLotHasADate() {
    var dated =
        evaluate(spec("100").lots(lot(p1, "NEW", NEW, 100, 100), lot(p2, "OLD", OLD, 100, 100)));

    assertThat(main(dated).batchIds()).containsExactly(p2);
    assertThat(main(dated).reasons())
        .filteredOn(reason -> reason.code() == ProposalReason.Code.OLDER_STOCK)
        .singleElement()
        .satisfies(reason -> assertThat(reason.since()).isEqualTo(OLD));

    var undated =
        evaluate(
            spec("100").lots(lot(p1, "NEW", NEW, 100, 100), lot(p2, "UNKNOWN", null, 100, 100)));

    // A lot without a date is not counted as old: the dated lot comes first, and no option
    // claims to be older stock.
    assertThat(main(undated).batchIds()).containsExactly(p1);
    assertThat(undated.agedByProductionDate()).isFalse();
    assertThat(undated.options())
        .allSatisfy(
            option ->
                assertThat(option.reasons())
                    .extracting(ProposalReason::code)
                    .doesNotContain(ProposalReason.Code.OLDER_STOCK));
  }

  @Test
  void lotsAreOrderedByKnownDateFirst() {
    ProposalLot dated = lot(p2, "P2", NEW, 100);
    ProposalLot undated = lot(p1, "P1", null, 100);
    ProposalLot older = lot(new UUID(0, 9), "P9", OLD, 100);

    assertThat(QuantityEvaluator.orderedLots(List.of(undated, dated, older)))
        .extracting(ProposalLot::batchId)
        .containsExactly(older.batchId(), p2, p1);
  }

  // ── Tolerance is a reference ─────────────────────────────────────────────

  @Test
  void optionsOutsideTheAgreedToleranceAreShownAndMarked_neverWithheld() {
    var result =
        evaluate(
            spec("500")
                .lots(lot(p1, "P1", null, 100, 100, 100), lot(p2, "P2", null, 100, 100, 100))
                .confirmed(Set.of(p1, p2))
                .tolerance("10", "10"));

    assertThat(byQuantity(result, "600").toleranceStatus()).isEqualTo(ToleranceStatus.OUTSIDE);
    assertThat(byQuantity(result, "300").toleranceStatus()).isEqualTo(ToleranceStatus.OUTSIDE);
    // Nothing meets 500 m within 10% without leaving one or two pieces: the nearest above opens.
    assertThat(main(result).quantity()).isEqualByComparingTo("600");
    assertThat(main(result).leavesRemnant()).isFalse();
  }

  @Test
  void zeroOnASideIsAnExplicitValue_noDeviationThatWayIsWithin() {
    var result = evaluate(spec("250").lots(lot(p1, "P1", null, 100, 100, 100)).tolerance("0", "0"));

    assertThat(result.options())
        .isNotEmpty()
        .allSatisfy(
            option -> assertThat(option.toleranceStatus()).isEqualTo(ToleranceStatus.OUTSIDE));
  }

  @Test
  void aMissingToleranceSideIsRefused_neverReadAsZero() {
    ProposalLot stock = lot(p1, "P1", null, 100, 100, 100);

    assertThatThrownBy(() -> evaluate(spec("250").lots(stock).noTolerance(true, false)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> evaluate(spec("250").lots(stock).noTolerance(false, true)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── The main proposal over every combination within tolerance ────────────

  @Test
  void closingTwoLotsWithinTolerance_beatsTheNearestCombinationThatLeavesASinglePiece() {
    // The closing review's counterexample (2026-10-13). The nearest exact search alone found
    // A + 100 m of B (fewer lots, older first), leaving a single 50 m piece in B.
    UUID a = new UUID(0, 21);
    UUID b = new UUID(0, 22);
    UUID c = new UUID(0, 23);
    var result =
        evaluate(
            spec("300")
                .lots(
                    lot(a, "A", OLD, 100, 100),
                    lot(b, "B", Instant.parse("2025-09-01T00:00:00Z"), 50, 50, 50),
                    lot(c, "C", NEW, 75, 75))
                .confirmed(Set.of(a, b, c))
                .tolerance("5", "5"));

    QuantityOption main = main(result);
    assertThat(main.quantity()).isEqualByComparingTo("300");
    assertThat(main.batchIds()).containsExactlyInAnyOrder(b, c);
    assertThat(main.leavesRemnant()).isFalse();
    assertThat(main.lots()).allSatisfy(part -> assertThat(part.closesLot()).isTrue());
    assertThat(result.searchComplete()).isTrue();
    assertThat(result.status()).isEqualTo(EvaluationStatus.EXACT);
  }

  // ── Free stock first; held stock only when free cannot reach the lower limit

  @Test
  void aFreeOptionWithinToleranceKeepsHeldStockOutOfTheOptions() {
    UUID heldPiece = new UUID(1, 1);
    var result =
        evaluate(
            spec("300")
                .lots(
                    lot(p1, "HELD", OLD, pieces(heldPiece, 300)),
                    lot(p2, "FREE", NEW, 100, 100, 95))
                .singleLot()
                .tolerance("5", "5")
                .holds(verifiedHold(heldPiece, p1, "300")));

    assertThat(result.options())
        .isNotEmpty()
        .allSatisfy(
            option -> {
              assertThat(option.source()).isEqualTo(StockSource.FREE);
              assertThat(option.batchIds()).containsOnly(p2);
            });
    assertThat(main(result).quantity()).isEqualByComparingTo("295");
  }

  @Test
  void singleLotStockSpreadOverLotsIsNotSufficient_heldStockBecomesAnExplainedAlternative() {
    UUID heldPiece = new UUID(1, 1);
    var result =
        evaluate(
            spec("500")
                .lots(
                    lot(p1, "A", OLD, 100, 100, 100),
                    lot(p2, "B", OLD, 100, 100, 100),
                    lot(p3, "HELD", NEW, pieces(heldPiece, 500)))
                .singleLot()
                .tolerance("5", "5")
                .holds(verifiedHold(heldPiece, p3, "500")));

    // 600 m free in total, but no single lot reaches 500 m within 5%.
    assertThat(main(result).source()).isEqualTo(StockSource.FREE);
    assertThat(main(result).lotCount()).isEqualTo(1);
    QuantityOption held = byQuantity(result, "500");
    assertThat(held.source()).isEqualTo(StockSource.HELD);
    assertThat(held.blockedReason()).isEqualTo(BlockedReason.HELD_BY_ANOTHER_QUOTE);
    assertThat(held.reasons().getFirst().code())
        .isEqualTo(ProposalReason.Code.FREE_STOCK_INSUFFICIENT);
    assertThat(held.heldStock())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.verified()).isTrue();
              assertThat(entry.quoteNumber()).isEqualTo("Q-7");
              assertThat(entry.pieceIds()).containsExactly(heldPiece);
            });
  }

  @Test
  void aQuantityIntentWithoutPiecesCapsWhatTheLotOffersFree() {
    var result =
        evaluate(
            spec("200").lots(lot(p1, "P1", null, 100, 100, 100, 100)).holds(intentOnly(p1, "250")));

    // 400 m eligible, 250 m intended by a quote: 150 m free, so one free piece of 100 m.
    assertThat(main(result).source()).isEqualTo(StockSource.FREE);
    assertThat(main(result).quantity()).isEqualByComparingTo("100");
    assertThat(result.options())
        .filteredOn(option -> option.source() == StockSource.FREE)
        .allSatisfy(
            option -> assertThat(option.quantity()).isLessThanOrEqualTo(new BigDecimal("150")));
  }

  @Test
  void anUnverifiedHoldIsNeverFree_andNamesNoQuote() {
    UUID unverified = new UUID(1, 2);
    var result =
        evaluate(
            spec("200")
                .lots(
                    lot(p1, "P1", null, both(pieces(unverified, 100), pieces(new UUID(1, 3), 100))))
                .holds(
                    new QuantityEvaluator.StockHolds(
                        Map.of(
                            unverified,
                            new QuantityEvaluator.Holder(
                                null, UUID.randomUUID(), null, null, null, false)),
                        Map.of())));

    assertThat(result.options())
        .isNotEmpty()
        .allSatisfy(
            option -> {
              assertThat(option.source()).isEqualTo(StockSource.HELD);
              assertThat(option.blockedReason()).isEqualTo(BlockedReason.HOLD_UNVERIFIED);
              assertThat(option.heldStock())
                  .allSatisfy(entry -> assertThat(entry.quoteNumber()).isNull());
            });
  }

  // ── Compatibility is a prerequisite ──────────────────────────────────────

  @Test
  void s05_unconfirmedCombinationsArePendingAndBlocked_andNeverTheMain() {
    var result =
        evaluate(
            spec("500")
                .lots(lot(p1, "P1", null, 100, 100, 100), lot(p2, "P2", null, 100, 100, 100)));

    assertThat(main(result).compatibility()).isEqualTo(Compatibility.SINGLE_LOT);
    assertThat(main(result).quantity()).isEqualByComparingTo("300");
    assertThat(result.options())
        .filteredOn(option -> option.compatibility() == Compatibility.PENDING_CONFIRMATION)
        .isNotEmpty()
        .allSatisfy(
            option -> {
              assertThat(option.blockedReason())
                  .isEqualTo(BlockedReason.COMPATIBILITY_CONFIRMATION_REQUIRED);
              assertThat(option.lotCount()).isGreaterThan(1);
            });
  }

  @Test
  void r11_singleLotRequirementNeverCombinesLots() {
    var result =
        evaluate(
            spec("500")
                .lots(lot(p1, "P1", null, 100, 100, 100), lot(p2, "P2", null, 100, 100, 100))
                .confirmed(Set.of(p1, p2))
                .singleLot());

    assertThat(result.options()).allSatisfy(option -> assertThat(option.lotCount()).isEqualTo(1));
    assertThat(main(result).quantity()).isEqualByComparingTo("300");
  }

  // ── Manual pick ──────────────────────────────────────────────────────────

  @Test
  void aManualPickAcrossLotsBreakingTheSingleLotRequirementIsBlocked() {
    UUID a = new UUID(2, 1);
    UUID b = new UUID(2, 2);
    var result =
        evaluate(
            spec("200")
                .lots(
                    lot(p1, "P1", null, both(pieces(a, 100), pieces(new UUID(2, 3), 100))),
                    lot(p2, "P2", null, both(pieces(b, 100), pieces(new UUID(2, 4), 100))))
                .confirmed(Set.of(p1, p2))
                .singleLot()
                .manual(a, b));

    QuantityOption manual =
        result.options().stream()
            .filter(option -> option.placement() == OptionPlacement.MANUAL)
            .findFirst()
            .orElseThrow();
    assertThat(manual.quantity()).isEqualByComparingTo("200");
    assertThat(manual.blockedReason()).isEqualTo(BlockedReason.SINGLE_LOT_REQUIRED);
    assertThat(manual.optionKey()).startsWith("M:");
    assertThat(result.options().get(result.mainOptionIndex()).placement())
        .isEqualTo(OptionPlacement.MAIN);
  }

  @Test
  void aPickedPieceThatIsNotOfferableIsReported_andNoManualOptionIsBuilt() {
    UUID pending = new UUID(3, 1);
    List<ProposalPiece> pieces = new ArrayList<>(eligible(p1, 100, 100));
    pieces.add(
        new ProposalPiece(pending, "X", new BigDecimal("50"), PieceState.UNKNOWN, "FLAGGED", 0L));
    var evaluation =
        QuantityEvaluator.evaluate(
            spec("100")
                .lots(
                    new ProposalLot(
                        p1,
                        "P1",
                        null,
                        PrimaryMeasure.LENGTH,
                        "M",
                        WidthEvidence.NOT_REQUIRED,
                        pieces))
                .manual(pending)
                .input());

    assertThat(evaluation.manualUnavailablePieceIds()).containsExactly(pending);
    assertThat(evaluation.result().options())
        .noneMatch(option -> option.placement() == OptionPlacement.MANUAL);
  }

  // ── Evidence that stays unknown ──────────────────────────────────────────

  @Test
  void s09_unknownPiecesAreCountedAndNeverUsed_andNoLotIsSaidToClose() {
    List<ProposalPiece> pieces = new ArrayList<>(eligible(p1, 100, 100));
    pieces.add(
        new ProposalPiece(UUID.randomUUID(), "X", null, PieceState.UNKNOWN, "MEASURE_MISSING", 0L));
    var result =
        evaluate(
            spec("500")
                .lots(
                    new ProposalLot(
                        p1,
                        "P1",
                        null,
                        PrimaryMeasure.LENGTH,
                        "M",
                        WidthEvidence.NOT_REQUIRED,
                        pieces)));

    assertThat(result.unknownPieces()).isEqualTo(1);
    assertThat(result.unknownReasons()).contains("MEASURE_MISSING");
    assertThat(result.options())
        .allSatisfy(
            option -> {
              assertThat(option.lots().getFirst().closesLot()).isFalse();
              assertThat(option.lots().getFirst().exhausted()).isFalse();
            });
  }

  @Test
  void aSearchLimitIsReported_andTheResultIsNotCalledComplete() {
    double[] many = new double[WholePieceSearch.MAX_PIECES_PER_LOT + 1];
    java.util.Arrays.fill(many, 10);
    var result = evaluate(spec("95").lots(lot(p1, "P1", null, many)));

    assertThat(result.searchComplete()).isFalse();
    assertThat(result.unknownReasons()).contains("EVALUATION_LIMIT");
  }

  @Test
  void millimetresAreNotRoundedIntoAnExactMatch() {
    var result = evaluate(spec("0.003").lots(lot(p1, "P", null, 0.001, 0.003)));

    assertThat(result.options()).isNotEmpty();
    assertThat(result.options())
        .filteredOn(option -> option.difference().signum() == 0)
        .allSatisfy(option -> assertThat(option.quantity()).isEqualByComparingTo("0.003"));
  }

  @Test
  void precisionBeyondSupportedResolutionStaysUnknown() {
    var result = evaluate(spec("0.0001"));

    assertThat(result.status()).isEqualTo(EvaluationStatus.UNKNOWN);
    assertThat(result.options()).isEmpty();
    assertThat(result.mainOptionIndex()).isNull();
  }

  @Test
  void theFingerprintChangesWhenAPieceOrAHoldChanges() {
    ProposalLot before = lot(p1, "P1", null, 100, 100, 100);
    ProposalLot after = lot(p1, "P1", null, 100, 100, 99);

    String a = QuantityEvaluator.evaluate(spec("200").lots(before).input()).evidenceFingerprint();
    String b = QuantityEvaluator.evaluate(spec("200").lots(after).input()).evidenceFingerprint();
    String c =
        QuantityEvaluator.evaluate(spec("200").lots(before).holds(intentOnly(p1, "50")).input())
            .evidenceFingerprint();

    assertThat(a).hasSize(64).isNotEqualTo(b).isNotEqualTo(c);
  }

  // ── The tenant's lot-health minimum (Fatih, 2026-10-14) ───────────────────

  @Test
  void withATenantMinimumOfFive_aLotKeepsFivePieces_orCloses() {
    LotHealthPolicy keepFive = new LotHealthPolicy(5, new BigDecimal("0.51"));
    var spec = spec("400").lots(lot(p1, "P1", OLD, 100, 100, 100, 100, 100, 100, 100, 100));

    // 400 m would keep four pieces: under the minimum. The nearest above closes the lot; the
    // best below keeps five.
    QuantityEvaluationResult result = QuantityEvaluator.evaluate(spec.input(), keepFive).result();

    assertThat(quantities(result)).containsExactly("800", "300");
    assertThat(main(result).quantity()).isEqualByComparingTo("800");
    assertThat(main(result).lots().getFirst().closesLot()).isTrue();
    assertThat(byQuantity(result, "300").lots().getFirst().remainingEligiblePieces()).isEqualTo(5);
    // The standard minimum keeps four: 400 m is the opening.
    assertThat(main(evaluate(spec)).quantity()).isEqualByComparingTo("400");
  }

  @Test
  void withATenantMinimumOfFive_aManualPickKeepingFourLeavesARemnant() {
    LotHealthPolicy keepFive = new LotHealthPolicy(5, new BigDecimal("0.51"));
    ProposalLot eight = lot(p1, "P1", OLD, 100, 100, 100, 100, 100, 100, 100, 100);
    UUID[] four =
        eight.pieces().subList(0, 4).stream().map(ProposalPiece::stockUnitId).toArray(UUID[]::new);

    QuantityEvaluationResult result =
        QuantityEvaluator.evaluate(spec("400").lots(eight).manual(four).input(), keepFive).result();

    QuantityOption manual =
        result.options().stream()
            .filter(option -> option.placement() == OptionPlacement.MANUAL)
            .findFirst()
            .orElseThrow();
    assertThat(manual.leavesRemnant()).isTrue();
    assertThat(manual.lots().getFirst().remnantPieceIds()).hasSize(4);
    assertThat(manual.needsRemnantAcknowledgement()).isTrue();
  }

  // ── Short pieces (Fatih, 2026-10-14) ─────────────────────────────────────

  @Test
  void fatihsExample_closingTheRemnantLotOpens_beforeTheExactQuantity() {
    // 700 m with 5%: LOT-A 40 + 30 + 55 m of a lot whose pieces averaged 100 m as produced, so
    // 40 and 30 m are short and 55 m is its only normal piece; LOT-B 20 × 100 m. LOT-B alone gives
    // 700 m exactly; closing LOT-A with six pieces of LOT-B gives 725 m and opens. The exact
    // 700 m is a second option within the tolerance, not a card.
    UUID lotA = new UUID(0, 31);
    UUID lotB = new UUID(0, 32);
    double[] twenty = new double[20];
    java.util.Arrays.fill(twenty, 100);
    var result =
        evaluate(
            spec("700")
                .lots(
                    lot(lotA, "LOT-A", NEW, new BigDecimal("100"), 40, 30, 55),
                    lot(lotB, "LOT-B", OLD, twenty))
                .confirmed(Set.of(lotA, lotB))
                .tolerance("5", "5"));

    QuantityOption opening = main(result);
    assertThat(opening.quantity()).isEqualByComparingTo("725");
    assertThat(part(opening, lotA).closesLot()).isTrue();
    assertThat(part(opening, lotB).remainingEligiblePieces()).isEqualTo(14);
    assertThat(quantities(result)).containsExactly("725", "625");
    assertThat(byQuantity(result, "625").placement()).isEqualTo(OptionPlacement.BELOW);
  }

  @Test
  void aShortPieceGoesFirst_andNeverCountsAsKept() {
    // 100 + 100 + 100 + 100 + 40 m; the pieces at hand average 88 m, so 40 m is short.
    ProposalLot lot = lot(p1, "P1", OLD, 100, 100, 100, 100, 40);
    UUID shortPiece = lot.pieces().get(4).stockUnitId();

    var oneForty = evaluate(spec("140").lots(lot));
    var twoHundred = evaluate(spec("200").lots(lot).tolerance("0", "0"));

    assertThat(main(oneForty).quantity()).isEqualByComparingTo("140");
    assertThat(main(oneForty).lots().getFirst().pieceIds()).contains(shortPiece);
    assertThat(main(oneForty).lots().getFirst().remnant()).isFalse();
    // 200 m would leave 100 + 100 + 40: two normal pieces, under the minimum. The lot closes.
    assertThat(main(twoHundred).quantity()).isEqualByComparingTo("440");
    assertThat(quantities(twoHundred)).containsExactly("440", "140");
  }

  @Test
  void aManualPickLeavingOnlyShortPieces_leavesARemnant() {
    ProposalLot lot = lot(p1, "P1", OLD, new BigDecimal("100"), 100, 100, 100, 40);
    UUID[] normal =
        lot.pieces().subList(0, 3).stream().map(ProposalPiece::stockUnitId).toArray(UUID[]::new);

    var result = evaluate(spec("300").lots(lot).manual(normal));

    QuantityOption manual =
        result.options().stream()
            .filter(option -> option.placement() == OptionPlacement.MANUAL)
            .findFirst()
            .orElseThrow();
    assertThat(manual.leavesRemnant()).isTrue();
    assertThat(manual.lots().getFirst().remnantPieceIds())
        .containsExactly(lot.pieces().get(3).stockUnitId());
    assertThat(manual.lots().getFirst().remnantQuantity()).isEqualByComparingTo("40");
    // The computed option takes the short piece along and closes the lot.
    assertThat(main(result).quantity()).isEqualByComparingTo("340");
  }

  @Test
  void theAveragePieceAsProducedDecidesWhatIsShort_andIsPartOfTheEvidence() {
    // At hand 4 × 100 + 2 × 60 m. Over the pieces at hand (87 m) no piece is short: 200 m takes
    // two 100 m pieces and keeps four. As produced the lot averaged 150 m, so the 60 m pieces are
    // short: two 100 m pieces would leave two normal ones, and 220 m takes the short pieces first.
    var atHand = spec("200").lots(lot(p1, "P1", OLD, 100, 100, 100, 100, 60, 60));
    var asProduced =
        spec("200").lots(lot(p1, "P1", OLD, new BigDecimal("150"), 100, 100, 100, 100, 60, 60));

    assertThat(main(evaluate(atHand)).quantity()).isEqualByComparingTo("200");
    assertThat(main(evaluate(asProduced)).quantity()).isEqualByComparingTo("220");
    assertThat(main(evaluate(asProduced)).lots().getFirst().remainingEligiblePieces()).isEqualTo(3);
    assertThat(QuantityEvaluator.evaluate(asProduced.input()).evidenceFingerprint())
        .isNotEqualTo(QuantityEvaluator.evaluate(atHand.input()).evidenceFingerprint());
  }

  @Test
  void anAddedLotNeverMakesAnOptionOlder_the665Case() {
    // 700 m with 5%. The newer lot alone gives 700 m. A 35 m short piece of the older lot with
    // 700 m of the newer gives 735 m, and 100 m of it with 600 m gives 700 m: each adds a lot,
    // which is not older stock, and loses on fewer lots. The older lot is too deep to close
    // within the tolerance.
    UUID older = new UUID(0, 41);
    UUID newer = new UUID(0, 42);
    var result =
        evaluate(
            spec("700")
                .lots(
                    lot(older, "OLDER", OLD, 35, 100, 100, 100, 100, 100, 100, 100, 100),
                    lot(newer, "NEWER", NEW, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100))
                .confirmed(Set.of(older, newer))
                .tolerance("5", "5"));

    assertThat(main(result).quantity()).isEqualByComparingTo("700");
    assertThat(main(result).batchIds()).containsExactly(newer);
  }

  @Test
  void aChangedLotHealthMinimumMakesTheEarlierProposalStale() {
    var spec = spec("300").lots(lot(p1, "P1", OLD, 100, 100, 100, 100, 100, 100, 100, 100));

    String standard = QuantityEvaluator.evaluate(spec.input()).evidenceFingerprint();
    String keepFive =
        QuantityEvaluator.evaluate(spec.input(), new LotHealthPolicy(5, new BigDecimal("0.51")))
            .evidenceFingerprint();

    assertThat(keepFive).isNotEqualTo(standard);
    assertThat(
            QuantityEvaluator.evaluate(spec.input(), LotHealthPolicy.STANDARD)
                .evidenceFingerprint())
        .isEqualTo(standard);
  }

  // ── Fixtures ─────────────────────────────────────────────────────────────

  private static QuantityEvaluationResult evaluate(Spec spec) {
    return QuantityEvaluator.evaluate(spec.input()).result();
  }

  private static QuantityOption main(QuantityEvaluationResult result) {
    assertThat(result.mainOptionIndex()).as("main option index").isNotNull();
    return result.options().get(result.mainOptionIndex());
  }

  private static QuantityOption byQuantity(QuantityEvaluationResult result, String quantity) {
    return result.options().stream()
        .filter(option -> option.placement() != OptionPlacement.MANUAL)
        .filter(option -> option.quantity().compareTo(new BigDecimal(quantity)) == 0)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No " + quantity + " option in " + result.options()));
  }

  private static List<String> quantities(QuantityEvaluationResult result) {
    return result.options().stream()
        .map(option -> option.quantity().stripTrailingZeros().toPlainString())
        .toList();
  }

  private static QuantityOption.LotPart part(QuantityOption option, UUID lot) {
    return option.lots().stream()
        .filter(part -> part.batchId().equals(lot))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Lot " + lot + " not used by " + option));
  }

  private static QuantityEvaluator.StockHolds verifiedHold(UUID piece, UUID lot, String intent) {
    QuantityEvaluator.Holder holder =
        new QuantityEvaluator.Holder(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Q-7",
            "Sales Rep",
            LocalDate.parse("2026-10-30"),
            true);
    return new QuantityEvaluator.StockHolds(
        Map.of(piece, holder),
        Map.of(
            lot,
            new QuantityEvaluator.LotIntents(
                List.of(
                    new QuantityEvaluator.Intent(
                        holder, new BigDecimal(intent), "M", new BigDecimal(intent))),
                new BigDecimal(intent))));
  }

  private static QuantityEvaluator.StockHolds intentOnly(UUID lot, String quantity) {
    QuantityEvaluator.Holder holder =
        new QuantityEvaluator.Holder(
            UUID.randomUUID(), UUID.randomUUID(), "Q-8", "Sales Rep", null, true);
    return new QuantityEvaluator.StockHolds(
        Map.of(),
        Map.of(
            lot,
            new QuantityEvaluator.LotIntents(
                List.of(
                    new QuantityEvaluator.Intent(
                        holder, new BigDecimal(quantity), "M", new BigDecimal(quantity))),
                new BigDecimal(quantity))));
  }

  private static Spec spec(String requested) {
    return new Spec(requested);
  }

  /** A readable way to state one evaluation's inputs. */
  private static final class Spec {
    private final String requested;
    private List<ProposalLot> lots = List.of();
    private List<Set<UUID>> confirmed = List.of();
    private boolean singleLot;
    // Both limits are required (Fatih, 2026-10-13); a test about something else agrees 0% openly.
    private BigDecimal up = BigDecimal.ZERO;
    private BigDecimal down = BigDecimal.ZERO;
    private QuantityEvaluator.StockHolds holds = QuantityEvaluator.StockHolds.NONE;
    private List<UUID> manual = List.of();

    Spec(String requested) {
      this.requested = requested;
    }

    Spec lots(ProposalLot... values) {
      lots = List.of(values);
      return this;
    }

    @SafeVarargs
    final Spec confirmed(Set<UUID>... groups) {
      confirmed = List.of(groups);
      return this;
    }

    Spec singleLot() {
      singleLot = true;
      return this;
    }

    Spec tolerance(String upPct, String downPct) {
      up = new BigDecimal(upPct);
      down = new BigDecimal(downPct);
      return this;
    }

    Spec noTolerance(boolean upMissing, boolean downMissing) {
      up = upMissing ? null : up;
      down = downMissing ? null : down;
      return this;
    }

    Spec holds(QuantityEvaluator.StockHolds value) {
      holds = value;
      return this;
    }

    Spec manual(UUID... pieces) {
      manual = List.of(pieces);
      return this;
    }

    QuantityEvaluator.Input input() {
      return new QuantityEvaluator.Input(
          new BigDecimal(requested),
          "M",
          "M",
          new BigDecimal(requested),
          Function.identity(),
          lots,
          confirmed,
          List.of(),
          singleLot,
          up,
          down,
          holds,
          manual);
    }
  }

  private static ProposalLot lot(UUID id, String lotNo, Instant produced, double... metres) {
    return new ProposalLot(
        id,
        lotNo,
        produced,
        PrimaryMeasure.LENGTH,
        "M",
        WidthEvidence.NOT_REQUIRED,
        eligible(id, metres));
  }

  /** A lot that knows its average piece as produced. */
  private static ProposalLot lot(
      UUID id, String lotNo, Instant produced, BigDecimal averagePiece, double... metres) {
    return new ProposalLot(
        id,
        lotNo,
        produced,
        PrimaryMeasure.LENGTH,
        "M",
        WidthEvidence.NOT_REQUIRED,
        eligible(id, metres),
        averagePiece);
  }

  /** A lot of named pieces and further eligible ones. */
  private static ProposalLot lot(
      UUID id, String lotNo, Instant produced, List<ProposalPiece> named, double... metres) {
    List<ProposalPiece> all = new ArrayList<>(named);
    all.addAll(eligible(id, metres));
    return new ProposalLot(
        id, lotNo, produced, PrimaryMeasure.LENGTH, "M", WidthEvidence.NOT_REQUIRED, all);
  }

  private static List<ProposalPiece> both(List<ProposalPiece> left, List<ProposalPiece> right) {
    List<ProposalPiece> all = new ArrayList<>(left);
    all.addAll(right);
    return all;
  }

  /** One named eligible piece. */
  private static List<ProposalPiece> pieces(UUID id, double metres) {
    return List.of(
        new ProposalPiece(
            id,
            "N-" + id.getLeastSignificantBits(),
            BigDecimal.valueOf(metres),
            PieceState.ELIGIBLE,
            null,
            0L));
  }

  /** Eligible pieces with ids derived from the lot, so the same stock always has the same ids. */
  private static List<ProposalPiece> eligible(UUID lot, double... metres) {
    List<ProposalPiece> pieces = new ArrayList<>();
    for (double length : metres) {
      UUID id = new UUID(lot.getLeastSignificantBits() + 1_000_000L, pieces.size());
      pieces.add(
          new ProposalPiece(
              id, "R" + pieces.size(), BigDecimal.valueOf(length), PieceState.ELIGIBLE, null, 0L));
    }
    return pieces;
  }
}
