package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.inventory.lothealth.domain.LotHealthPolicy;
import com.fabricmanagement.inventory.lothealth.domain.PieceAverageBasis;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.core.stockunit.domain.PackageType;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.Holder;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.Input;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.Intent;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.LotIntents;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.StockHolds;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalPolicyCode;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityCoverage;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FabricRollProposalPolicyTest {
  static BigDecimal n(String value) {
    return new BigDecimal(value);
  }

  static UUID id(long number) {
    return new UUID(0, number);
  }

  static ProposalLot lot(long key, String... metres) {
    List<ProposalPiece> pieces =
        IntStream.range(0, metres.length)
            .mapToObj(
                i ->
                    new ProposalPiece(
                        id(key * 100 + i),
                        "ROLL-" + key + "-" + i,
                        n(metres[i]),
                        PieceState.ELIGIBLE,
                        null,
                        1L,
                        PackageType.ROLL,
                        n(metres[i])))
            .toList();
    return new ProposalLot(
        id(key),
        "LOT-" + key,
        Instant.parse("2026-01-01T00:00:00Z"),
        PrimaryMeasure.LENGTH,
        "M",
        WidthEvidence.NOT_REQUIRED,
        pieces,
        null,
        PieceAverageBasis.CURRENT_MEASUREMENTS,
        true);
  }

  static ProposalLot repeated(long key, int count, String metres) {
    return lot(key, Collections.nCopies(count, metres).toArray(String[]::new));
  }

  static Input input(
      String target, List<ProposalLot> lots, boolean single, StockHolds holds, List<UUID> manual) {
    return new Input(
        n(target),
        "M",
        "M",
        n(target),
        Function.identity(),
        lots,
        List.of(),
        List.of(),
        single,
        n("5"),
        n("5"),
        holds,
        manual,
        ProposalPolicyCode.FABRIC_ROLL_BASE);
  }

  static QuantityEvaluationResult evaluate(String target, ProposalLot... lots) {
    return QuantityEvaluator.evaluate(
            input(target, List.of(lots), false, StockHolds.NONE, List.of()))
        .result();
  }

  static QuantityOption main(QuantityEvaluationResult result) {
    return result.options().get(result.mainOptionIndex());
  }

  @Test
  void cleanupCompletion790OpensBefore665DespiteTolerance() {
    ProposalLot a = lot(1, "95", "95", "100");
    ProposalLot b = repeated(2, 12, "125");
    var result = evaluate("700", a, b);
    assertThat(result.options())
        .extracting(QuantityOption::canonicalQuantity)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(n("790"), n("665"));
    assertThat(main(result).toleranceStatus()).isEqualTo(QuantityOption.ToleranceStatus.OUTSIDE);
    assertThat(result.options().get(1).toleranceStatus())
        .isEqualTo(QuantityOption.ToleranceStatus.WITHIN);
    assertThat(result.effectiveMinHealthyPieces()).isEqualTo(4);
    assertThat(result.coverage().status()).isEqualTo(QuantityCoverage.Status.COVERABLE);
    assertThat(result.coverage().coveringOptionKey()).isEqualTo(main(result).optionKey());
    assertThat(main(result).lots())
        .anySatisfy(
            part -> {
              assertThat(part.batchId()).isEqualTo(a.batchId());
              assertThat(part.closesLot()).isTrue();
              assertThat(part.depletesFreePool()).isTrue();
            });
    assertThat(evaluate("700", b, a)).isEqualTo(result);
  }

  @Test
  void completionSwitchesPoolInsteadOfLeavingThreeNormals() {
    var a = lot(1, "95", "95", "100");
    var b = repeated(2, 7, "125");
    var c = repeated(3, 12, "125");
    var result = evaluate("700", a, b, c);
    assertThat(main(result).canonicalQuantity()).isEqualByComparingTo("790");
    assertThat(main(result).batchIds()).containsExactlyInAnyOrder(a.batchId(), c.batchId());
    // Without C, closing all seven B rolls is legal; absence of a nearer candidate is not no stock.
    assertThat(main(evaluate("700", a, b)).canonicalQuantity()).isEqualByComparingTo("1165");
  }

  @Test
  void aNormalLotIsNotClosedJustToIncreaseSalesForTheSameCleanupAnchor() {
    var result = evaluate("350", repeated(1, 8, "100"));
    assertThat(main(result).canonicalQuantity()).isEqualByComparingTo("400");
    assertThat(main(result).lots().getFirst().remainingEligiblePieces()).isEqualTo(4);
  }

  @Test
  void singleLotAndManualConfirmationCannotManufactureCoverage() {
    var a = repeated(1, 4, "100");
    var b = repeated(2, 4, "100");
    var result =
        QuantityEvaluator.evaluate(
                input(
                    "700",
                    List.of(a, b),
                    true,
                    StockHolds.NONE,
                    List.of(
                        a.pieces().getFirst().stockUnitId(), b.pieces().getFirst().stockUnitId())))
            .result();
    assertThat(result.coverage().status()).isEqualTo(QuantityCoverage.Status.BELOW_REQUEST_ONLY);
    assertThat(result.coverage().reasons()).contains(QuantityCoverage.Reason.SINGLE_LOT_REQUIRED);
    assertThat(result.options())
        .filteredOn(option -> option.lotCount() > 1)
        .allSatisfy(
            option ->
                assertThat(option.blockedReason())
                    .isEqualTo(QuantityOption.BlockedReason.SINGLE_LOT_REQUIRED));
    assertThat(evaluate("700", a, b).coverage().status())
        .isEqualTo(QuantityCoverage.Status.COVERABLE);
  }

  @Test
  void manualDuplicateKeepsOpeningAndCoverageKey() {
    var a = lot(1, "100");
    var result =
        QuantityEvaluator.evaluate(
                input(
                    "100",
                    List.of(a),
                    false,
                    StockHolds.NONE,
                    List.of(a.pieces().getFirst().stockUnitId())))
            .result();
    assertThat(result.options()).hasSize(1);
    assertThat(result.mainOptionIndex()).isZero();
    assertThat(main(result).placement()).isEqualTo(QuantityOption.OptionPlacement.MAIN);
    assertThat(result.coverage().coveringOptionKey()).isEqualTo(main(result).optionKey());
  }

  @Test
  void heldOrUnknownOrUnsupportedPhysicalPiecesPreventClosure() {
    var a = repeated(1, 8, "100");
    Holder owner = new Holder(id(800), id(801), "Q", "Rep", null, true);
    Map<UUID, Holder> pinned =
        a.pieces().subList(3, 8).stream()
            .collect(java.util.stream.Collectors.toMap(ProposalPiece::stockUnitId, piece -> owner));
    var result =
        QuantityEvaluator.evaluate(
                input("300", List.of(a), false, new StockHolds(pinned, Map.of()), List.of()))
            .result();
    assertThat(main(result).lots().getFirst().depletesFreePool()).isTrue();
    assertThat(main(result).lots().getFirst().closesLot()).isFalse();

    for (PieceState state : List.of(PieceState.UNKNOWN, PieceState.EXCLUDED)) {
      var piece =
          new ProposalPiece(
              id(999),
              "OTHER",
              n("10"),
              state,
              "QUALITY_PENDING",
              1L,
              state == PieceState.EXCLUDED ? PackageType.CARTON : PackageType.ROLL,
              null);
      List<ProposalPiece> pieces = new ArrayList<>(a.pieces().subList(0, 3));
      pieces.add(piece);
      var mixed =
          new ProposalLot(
              a.batchId(),
              a.lotNo(),
              a.productionDate(),
              a.measure(),
              "M",
              a.widthEvidence(),
              pieces,
              n("100"),
              PieceAverageBasis.CURRENT_MEASUREMENTS,
              true);
      assertThat(main(evaluate("300", mixed)).lots().getFirst().closesLot()).isFalse();
    }
  }

  @Test
  void inventoryGapMakesClosureUnknownButDoesNotErasePositiveCoverage() {
    var a = lot(1, "100");
    var gap =
        new ProposalLot(
            a.batchId(),
            a.lotNo(),
            a.productionDate(),
            a.measure(),
            "M",
            a.widthEvidence(),
            a.pieces(),
            n("100"),
            PieceAverageBasis.CURRENT_MEASUREMENTS,
            false);
    var result = evaluate("100", gap);
    assertThat(main(result).lots().getFirst().closesLot()).isNull();
    assertThat(result.coverage().status()).isEqualTo(QuantityCoverage.Status.COVERABLE);
    assertThat(evaluate("200", gap).coverage().status()).isEqualTo(QuantityCoverage.Status.UNKNOWN);
  }

  @Test
  void unsupportedPackageCannotRaiseNormalCountOrProduceFalseAbsence() {
    var a = lot(1, "100");
    var nonRoll =
        new ProposalPiece(
            id(99),
            "CARTON",
            n("100"),
            PieceState.ELIGIBLE,
            null,
            1L,
            PackageType.CARTON,
            n("100"));
    var carton =
        new ProposalLot(
            a.batchId(),
            a.lotNo(),
            a.productionDate(),
            a.measure(),
            "M",
            a.widthEvidence(),
            List.of(nonRoll));
    var result = evaluate("100", carton);
    assertThat(result.options()).isEmpty();
    assertThat(result.coverage().status()).isEqualTo(QuantityCoverage.Status.UNKNOWN);
    assertThat(result.coverage().reasons()).contains(QuantityCoverage.Reason.UNSUPPORTED_PACKAGE);
  }

  @Test
  void canonicalQuantityDecidesToleranceEvenWhenDisplayRoundsToRequest() {
    var a = lot(1, "100.001");
    var input =
        new Input(
            n("100"),
            "M",
            "M",
            n("100"),
            value -> value.setScale(0, java.math.RoundingMode.HALF_UP),
            List.of(a),
            List.of(),
            List.of(),
            false,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            StockHolds.NONE,
            List.of(),
            ProposalPolicyCode.FABRIC_ROLL_BASE);
    var result = QuantityEvaluator.evaluate(input).result();
    assertThat(main(result).quantity()).isEqualByComparingTo("100");
    assertThat(main(result).kind()).isEqualTo(QuantityOption.OptionKind.ABOVE);
    assertThat(main(result).toleranceStatus()).isEqualTo(QuantityOption.ToleranceStatus.OUTSIDE);
    assertThat(result.status()).isEqualTo(QuantityEvaluationResult.EvaluationStatus.OPTIONS);
  }

  @Test
  void physicalCleanupRanksBeforeFreePoolOnlyCleanup() {
    var physical = lot(1, "100", "100", "100");
    var freeOnly =
        new ProposalLot(
            id(2),
            "FREE",
            physical.productionDate(),
            PrimaryMeasure.LENGTH,
            "M",
            WidthEvidence.NOT_REQUIRED,
            List.of(
                new ProposalPiece(
                    id(201),
                    "R",
                    n("290"),
                    PieceState.ELIGIBLE,
                    null,
                    1L,
                    PackageType.ROLL,
                    n("290")),
                new ProposalPiece(
                    id(202),
                    "U",
                    null,
                    PieceState.UNKNOWN,
                    "MEASURE_MISSING",
                    1L,
                    PackageType.ROLL,
                    null)));
    var result = evaluate("350", physical, freeOnly, repeated(3, 12, "100"));
    assertThat(main(result).batchIds())
        .contains(physical.batchId())
        .doesNotContain(freeOnly.batchId());
  }

  @Test
  void standardPolicyStillKeepsThreeAndDoesNotAcquireFabricOrdering() {
    var a = repeated(1, 7, "100");
    var legacy =
        new Input(
            n("400"),
            "KG",
            "KG",
            n("400"),
            Function.identity(),
            List.of(a),
            List.of(),
            List.of(),
            false,
            n("5"),
            n("5"),
            StockHolds.NONE,
            List.of());
    var result = QuantityEvaluator.evaluate(legacy).result();
    assertThat(result.policyCode()).isEqualTo(ProposalPolicyCode.STANDARD);
    assertThat(result.effectiveMinHealthyPieces()).isEqualTo(3);
    assertThat(main(result).canonicalQuantity()).isEqualByComparingTo("400");
    assertThat(main(evaluate("400", a)).canonicalQuantity()).isEqualByComparingTo("700");
  }

  @Test
  void limitsAndUnknownMeasurementsNeverProveAbsence() {
    var many =
        IntStream.rangeClosed(1, 13).mapToObj(i -> lot(i, "100")).toArray(ProposalLot[]::new);
    var limited = evaluate("700", many);
    assertThat(limited.searchComplete()).isFalse();
    assertThat(limited.coverage().status()).isEqualTo(QuantityCoverage.Status.UNKNOWN);
    assertThat(limited.coverage().reasons()).contains(QuantityCoverage.Reason.SEARCH_LIMIT);
    assertThat(evaluate("700", repeated(1, 61, "100")).searchComplete()).isFalse();
    var a = lot(1, "100");
    var unknown =
        new ProposalLot(
            a.batchId(),
            a.lotNo(),
            null,
            a.measure(),
            "M",
            a.widthEvidence(),
            List.of(
                new ProposalPiece(
                    id(102),
                    "U",
                    null,
                    PieceState.UNKNOWN,
                    "MEASURE_MISSING",
                    1L,
                    PackageType.ROLL,
                    null)));
    assertThat(evaluate("100", unknown).coverage().status())
        .isEqualTo(QuantityCoverage.Status.UNKNOWN);
  }

  @Test
  void healthyRemainderReasonRequiresAConcreteRelaxedSelection() {
    var a = repeated(1, 7, "100");
    Holder owner = new Holder(id(7), id(8), "Q", "Rep", null, true);
    var holds =
        new StockHolds(
            Map.of(),
            Map.of(
                a.batchId(),
                new LotIntents(List.of(new Intent(owner, n("200"), "M", n("200"))), n("200"))));
    var result =
        QuantityEvaluator.evaluate(input("400", List.of(a), false, holds, List.of())).result();
    assertThat(result.coverage().status()).isEqualTo(QuantityCoverage.Status.BELOW_REQUEST_ONLY);
    assertThat(result.coverage().reasons())
        .contains(QuantityCoverage.Reason.HEALTHY_REMAINDER_REQUIRED);
    assertThat(main(result).canonicalQuantity()).isEqualByComparingTo("300");
  }

  @Test
  void fourRemainingPhysicalRollsDoNotCountAsFourNormalsWhenOneIsShort() {
    var a = lot(1, "100", "100", "100", "100", "100", "100", "100", "50.999");
    var evidence =
        new ProposalLot(
            a.batchId(),
            a.lotNo(),
            a.productionDate(),
            a.measure(),
            "M",
            a.widthEvidence(),
            a.pieces(),
            n("100"),
            PieceAverageBasis.INITIAL_MEASUREMENTS,
            true);
    var result = evaluate("400", evidence);
    assertThat(main(result).canonicalQuantity()).isEqualByComparingTo("750.999");
    assertThat(new LotHealthPolicy(4, n("0.51")).isShort(n("51"), n("100"))).isFalse();
    assertThat(new LotHealthPolicy(4, n("0.51")).isShort(n("50.999"), n("100"))).isTrue();
  }

  @Test
  void limitInAnotherSingleLotRunDoesNotEraseAnApplicableCoveringProof() {
    var a = lot(1, "700");
    var huge = repeated(2, 61, "100");
    var result =
        QuantityEvaluator.evaluate(input("700", List.of(a, huge), true, StockHolds.NONE, List.of()))
            .result();
    assertThat(result.searchComplete()).isFalse();
    assertThat(result.coverage().status()).isEqualTo(QuantityCoverage.Status.COVERABLE);
    assertThat(result.coverage().reasons()).contains(QuantityCoverage.Reason.SEARCH_LIMIT);
  }

  @Test
  void subResolutionAndHugeValuesRemainUnknownInsteadOfRoundingToAnExactOption() {
    assertThat(evaluate("100.0001", lot(1, "100")).coverage().status())
        .isEqualTo(QuantityCoverage.Status.UNKNOWN);
    assertThat(evaluate("100", lot(1, "999999999999999999999")).coverage().status())
        .isEqualTo(QuantityCoverage.Status.UNKNOWN);
  }

  @Test
  void sharedFreeCalculationUsesEachLinesOwnPinnedPieces() {
    var a = lot(1, "120", "880");
    Holder owner = new Holder(id(7), id(8), "Q", "Rep", null, true);
    for (String intent : List.of("100", "150")) {
      var holds =
          new StockHolds(
              Map.of(a.pieces().getFirst().stockUnitId(), owner),
              Map.of(
                  a.batchId(),
                  new LotIntents(
                      List.of(new Intent(owner, n(intent), "M", n(intent))), n(intent))));
      assertThat(StockPools.lotFree(holds, a).canonical())
          .isEqualByComparingTo(intent.equals("100") ? "880" : "850");
    }
    Holder other = new Holder(id(9), id(10), "Q2", "Rep", null, true);
    var holds =
        new StockHolds(
            Map.of(a.pieces().getFirst().stockUnitId(), owner),
            Map.of(
                a.batchId(),
                new LotIntents(
                    List.of(
                        new Intent(owner, n("100"), "M", n("100")),
                        new Intent(other, n("100"), "M", n("100"))),
                    n("200"))));
    assertThat(StockPools.lotFree(holds, a).canonical()).isEqualByComparingTo("780");
    assertThat(
            StockPools.lotFree(
                    new StockHolds(
                        Map.of(a.pieces().getFirst().stockUnitId(), Holder.unverified()), Map.of()),
                    a)
                .canonical())
        .isNull();
  }
}
