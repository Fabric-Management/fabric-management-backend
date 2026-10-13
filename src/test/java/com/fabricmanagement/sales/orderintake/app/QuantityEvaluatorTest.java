package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * STOCK-PROPOSAL-2 and SOI S04, S05, S09, A01, A04 on the evaluation level: the main proposal from
 * the free options that meet the request within the tolerance, ranked tenant-first (no single piece
 * left, lots that fit taken whole, older stock, fewer lots, closeness), otherwise the best
 * construction; the nearest options below and above; quantity order with the main index; held stock
 * only when free stock has no option within tolerance; and the manual pick. Data is fictional.
 */
class QuantityEvaluatorTest {

  private static final Instant OLD = Instant.parse("2025-03-01T00:00:00Z");
  private static final Instant NEW = Instant.parse("2026-06-01T00:00:00Z");

  private final UUID p1 = new UUID(0, 1);
  private final UUID p2 = new UUID(0, 2);
  private final UUID p3 = new UUID(0, 3);

  // ── The decision record's examples ───────────────────────────────────────

  @Test
  void pieces120_110_90For200_theOnlyOptionWithinToleranceIsTheMain_evenLeavingASinglePiece() {
    var result = evaluate(spec("200").lots(lot(p1, "P1", null, 120, 110, 90)).tolerance("5", "5"));

    // 110 + 90 meets the request but leaves the 120 m piece alone; it is still the only option
    // within the tolerance, so it is the main one, and applying it asks for confirmation.
    QuantityOption main = main(result);
    assertThat(main.quantity()).isEqualByComparingTo("200");
    assertThat(main.leavesSingleRemnant()).isTrue();
    assertThat(main.lots().getFirst().remnantQuantity()).isEqualByComparingTo("120");
    assertThat(main.reasons())
        .extracting(ProposalReason::code)
        .containsSubsequence(
            ProposalReason.Code.LEAVES_SINGLE_PIECE, ProposalReason.Code.EXACT_REQUEST);
    // The construction that leaves no single piece is kept beside it, not hidden.
    QuantityOption below = byQuantity(result, "120");
    assertThat(below.placement()).isEqualTo(OptionPlacement.BELOW);
    assertThat(below.nearest()).isTrue();
    assertThat(below.leavesSingleRemnant()).isFalse();
    assertThat(below.toleranceStatus()).isEqualTo(ToleranceStatus.OUTSIDE);
    assertThat(below.reasons())
        .extracting(ProposalReason::code)
        .contains(ProposalReason.Code.NO_SINGLE_PIECE_LEFT);
  }

  @Test
  void withoutAnyOptionWithinTolerance_theTenantFirstConstructionIsTheMain() {
    var result = evaluate(spec("200").lots(lot(p1, "P1", null, 120, 110, 90)).tolerance("0", "0"));

    // 200 m is exact, so it is within even a zero tolerance; here the request moves out of reach.
    var unreachable =
        evaluate(spec("205").lots(lot(p1, "P1", null, 120, 110, 90)).tolerance("0", "0"));

    assertThat(main(result).quantity()).isEqualByComparingTo("200");
    assertThat(main(unreachable).quantity()).isEqualByComparingTo("120");
    assertThat(main(unreachable).leavesSingleRemnant()).isFalse();
    assertThat(main(unreachable).toleranceStatus()).isEqualTo(ToleranceStatus.OUTSIDE);
  }

  @Test
  void withinTolerance_noSinglePieceAndLotClosureComeBeforeCloseness() {
    var result =
        evaluate(spec("200").lots(lot(p1, "P1", null, 100, 100, 100)).tolerance("50", "50"));

    // 200 m is exact but leaves one piece; 300 m closes the lot; 100 m leaves two pieces.
    assertThat(main(result).quantity()).isEqualByComparingTo("300");
    assertThat(main(result).lots().getFirst().closesLot()).isTrue();
    assertThat(quantities(result)).containsExactly("100", "200", "300");
  }

  @Test
  void aLotYouCannotCombineWithIsNotTheMainWhenAnotherLotMeetsTheRequest() {
    var result =
        evaluate(
            spec("700")
                .lots(
                    lot(p1, "X", OLD, 100, 100, 100),
                    lot(p2, "Y", NEW, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100))
                .tolerance("5", "5"));

    // X is older and would close, but it cannot be combined with Y and is 400 m short.
    assertThat(main(result).quantity()).isEqualByComparingTo("700");
    assertThat(main(result).batchIds()).containsExactly(p2);
    assertThat(main(result).lots().getFirst().remainingEligiblePieces()).isEqualTo(3);
    QuantityOption x = byQuantity(result, "300");
    assertThat(x.placement()).isEqualTo(OptionPlacement.BELOW);
    assertThat(x.lots().getFirst().closesLot()).isTrue();
    assertThat(x.toleranceStatus()).isEqualTo(ToleranceStatus.OUTSIDE);
  }

  @Test
  void aLotThatFitsTheRemainingTargetIsClosed_insteadOfTakingOneOfItsPieces() {
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

    QuantityOption main = main(result);
    QuantityOption.LotPart closed = part(main, lot23087);
    assertThat(closed.quantity()).isEqualByComparingTo("290");
    assertThat(closed.closesLot()).isTrue();
    assertThat(part(main, newer).quantity()).isEqualByComparingTo("400");
    assertThat(main.quantity()).isEqualByComparingTo("690");
    assertThat(main.reasons())
        .extracting(ProposalReason::code)
        .contains(ProposalReason.Code.CLOSES_LOTS);
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
  void s04_closingBothConfirmedLotsIsTheMain_theNearestOptionsAreKeptBesideIt() {
    var result =
        evaluate(
            spec("500")
                .lots(lot(p1, "P1", null, 100, 100, 100), lot(p2, "P2", null, 100, 100, 100))
                .confirmed(Set.of(p1, p2))
                .tolerance("20", "20"));

    assertThat(quantities(result)).containsExactly("400", "500", "600");
    assertThat(main(result).quantity()).isEqualByComparingTo("600");
    assertThat(main(result).compatibility()).isEqualTo(Compatibility.CONFIRMED);
    assertThat(main(result).lots()).allSatisfy(part -> assertThat(part.closesLot()).isTrue());
    assertThat(byQuantity(result, "400").placement()).isEqualTo(OptionPlacement.BELOW);
    assertThat(byQuantity(result, "400").nearest()).isTrue();
    assertThat(byQuantity(result, "500").leavesSingleRemnant()).isTrue();
    assertThat(result.mainOptionIndex()).isEqualTo(2);
  }

  // ── Quantity order and the main index ────────────────────────────────────

  @Test
  void optionsAreInAscendingQuantity_andTheMainMaySitInTheMiddle() {
    var result = evaluate(spec("200").lots(lot(p1, "P1", null, 120, 110, 90, 60)));

    List<BigDecimal> ordered = result.options().stream().map(QuantityOption::quantity).toList();
    List<BigDecimal> sorted = new ArrayList<>(ordered);
    Collections.sort(sorted);
    assertThat(ordered).isEqualTo(sorted);
    assertThat(result.mainOptionIndex()).isNotNull();
    assertThat(result.options().get(result.mainOptionIndex()).placement())
        .isEqualTo(OptionPlacement.MAIN);
    assertThat(result.options()).filteredOn(o -> o.placement() == OptionPlacement.MAIN).hasSize(1);
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
    assertThat(byQuantity(result, "400").toleranceStatus()).isEqualTo(ToleranceStatus.OUTSIDE);
    assertThat(byQuantity(result, "500").toleranceStatus()).isEqualTo(ToleranceStatus.WITHIN);
    // The only option within 10% is the main one, though it leaves a single piece.
    assertThat(main(result).quantity()).isEqualByComparingTo("500");
    assertThat(main(result).leavesSingleRemnant()).isTrue();
  }

  @Test
  void aToleranceSideThatWasNotAgreedAllowsNoDeviation() {
    var result = evaluate(spec("250").lots(lot(p1, "P1", null, 100, 100, 100)));

    assertThat(result.options())
        .isNotEmpty()
        .allSatisfy(
            option -> assertThat(option.toleranceStatus()).isEqualTo(ToleranceStatus.OUTSIDE));
  }

  // ── Free stock first; held stock only when free has nothing within tolerance

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
    private BigDecimal up;
    private BigDecimal down;
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
