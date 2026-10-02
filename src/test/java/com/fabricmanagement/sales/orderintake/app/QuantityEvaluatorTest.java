package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult.EvaluationStatus;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.Compatibility;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** SOI S03, S04, S05, S09, S16 and A01–A04-b on the evaluation level. Data is fictional. */
class QuantityEvaluatorTest {

  @Test
  void millimetresAreNotRoundedIntoAnExactMatch() {
    var result =
        QuantityEvaluator.evaluate(
                input(
                    "0.003",
                    List.of(lot(new UUID(0, 5), "P", 0.001, 0.003)),
                    List.of(),
                    List.of(),
                    false,
                    null))
            .result();
    assertThat(result.options())
        .allSatisfy(
            option -> {
              if (option.kind() == OptionKind.EXACT
                  || option.kind() == OptionKind.REQUESTED_WITH_REMNANT) {
                assertThat(option.quantity()).isEqualByComparingTo("0.003");
              }
            });
    assertThat(result.options()).isNotEmpty();
  }

  @Test
  void precisionBeyondSupportedResolutionStaysUnknown() {
    var result =
        QuantityEvaluator.evaluate(input("0.0001", List.of(), List.of(), List.of(), false, null))
            .result();
    assertThat(result.status()).isEqualTo(EvaluationStatus.UNKNOWN);
    assertThat(result.options()).isEmpty();
  }

  private final UUID p1 = new UUID(0, 1);
  private final UUID p2 = new UUID(0, 2);

  @Test
  void s04_withConfirmedLots_optionsAre600And400AndTheRequestWithARemnantWarning() {
    List<ProposalLot> lots = List.of(lot(p1, "P1", 100, 100, 100), lot(p2, "P2", 100, 100, 100));

    QuantityEvaluationResult result =
        QuantityEvaluator.evaluate(
                input("500", lots, List.of(Set.of(p1, p2)), List.of(), false, null))
            .result();

    assertThat(result.status()).isEqualTo(EvaluationStatus.OPTIONS);
    assertThat(option(result, OptionKind.ABOVE).quantity()).isEqualByComparingTo("600");
    assertThat(option(result, OptionKind.ABOVE).compatibility()).isEqualTo(Compatibility.CONFIRMED);
    assertThat(option(result, OptionKind.ABOVE).differencePercent()).isEqualByComparingTo("20.0");
    QuantityOption below = option(result, OptionKind.BELOW);
    assertThat(below.quantity()).isEqualByComparingTo("400");
    assertThat(below.leavesSingleRemnant()).isFalse();
    QuantityOption requested = option(result, OptionKind.REQUESTED_WITH_REMNANT);
    assertThat(requested.quantity()).isEqualByComparingTo("500");
    assertThat(requested.leavesSingleRemnant()).isTrue();
    assertThat(requested.lots())
        .filteredOn(QuantityOption.LotPart::singleRemnant)
        .singleElement()
        .satisfies(lot -> assertThat(lot.remnantQuantity()).isEqualByComparingTo("100"));
    // 300 m is not offered when 400 m is closer (A02: closeness first).
    assertThat(result.options())
        .noneMatch(option -> option.quantity().compareTo(new BigDecimal("300")) == 0);
  }

  @Test
  void s05_withoutCompatibilityEvidence_multiLotOptionsArePendingAndSingleLotStaysDefinite() {
    List<ProposalLot> lots = List.of(lot(p1, "P1", 100, 100, 100), lot(p2, "P2", 100, 100, 100));

    QuantityEvaluationResult result =
        QuantityEvaluator.evaluate(input("500", lots, List.of(), List.of(), false, null)).result();

    assertThat(result.options())
        .filteredOn(option -> option.compatibility() == Compatibility.SINGLE_LOT)
        .singleElement()
        .satisfies(
            option -> {
              assertThat(option.kind()).isEqualTo(OptionKind.BELOW);
              assertThat(option.quantity()).isEqualByComparingTo("300");
              assertThat(option.lots().getFirst().exhausted()).isTrue();
            });
    assertThat(result.options())
        .filteredOn(option -> option.compatibility() == Compatibility.PENDING_CONFIRMATION)
        .extracting(QuantityOption::quantity)
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactlyInAnyOrder(new BigDecimal("600"), new BigDecimal("400"));
  }

  @Test
  void a04b_aConcreteToneAcceptanceMakesTheCombinationDefinite() {
    List<ProposalLot> lots = List.of(lot(p1, "P1", 100, 100, 100), lot(p2, "P2", 100, 100, 100));

    QuantityEvaluationResult result =
        QuantityEvaluator.evaluate(
                input("500", lots, List.of(), List.of(Set.of(p1, p2)), false, null))
            .result();

    assertThat(option(result, OptionKind.ABOVE).compatibility())
        .isEqualTo(Compatibility.TONE_ACCEPTED);
    assertThat(result.options())
        .noneMatch(option -> option.compatibility() == Compatibility.PENDING_CONFIRMATION);
  }

  @Test
  void r11_singleLotRequirementNeverCombinesLots() {
    List<ProposalLot> lots = List.of(lot(p1, "P1", 100, 100, 100), lot(p2, "P2", 100, 100, 100));

    QuantityEvaluationResult result =
        QuantityEvaluator.evaluate(
                input("500", lots, List.of(Set.of(p1, p2)), List.of(), true, null))
            .result();

    assertThat(result.options()).allSatisfy(option -> assertThat(option.lotCount()).isEqualTo(1));
    assertThat(option(result, OptionKind.BELOW).quantity()).isEqualByComparingTo("300");
  }

  @Test
  void s03_514IsProposedAndTheRequestIsKept() {
    List<ProposalLot> lots = List.of(lot(p1, "P3", 102, 103, 101, 104, 104));

    QuantityEvaluationResult result =
        QuantityEvaluator.evaluate(input("500", lots, List.of(), List.of(), false, null)).result();

    assertThat(result.requestedQuantity()).isEqualByComparingTo("500");
    assertThat(option(result, OptionKind.ABOVE).quantity()).isEqualByComparingTo("514");
    assertThat(option(result, OptionKind.ABOVE).lots().getFirst().exhausted()).isTrue();
  }

  @Test
  void a03_anAgreedToleranceWithholdsOptionsBeyondIt() {
    List<ProposalLot> lots = List.of(lot(p1, "P1", 100, 100, 100), lot(p2, "P2", 100, 100, 100));

    QuantityEvaluationResult result =
        QuantityEvaluator.evaluate(
                input("500", lots, List.of(Set.of(p1, p2)), List.of(), false, new BigDecimal("10")))
            .result();

    assertThat(result.options()).noneMatch(option -> option.kind() == OptionKind.ABOVE);
    assertThat(result.optionsOutsideAgreedTolerance()).isEqualTo(1);
  }

  @Test
  void s09_unknownPiecesAreCountedAndNeverUsed() {
    List<ProposalPiece> pieces = new ArrayList<>(eligible(100, 100));
    pieces.add(
        new ProposalPiece(UUID.randomUUID(), "X", null, PieceState.UNKNOWN, "MEASURE_MISSING", 0L));
    pieces.add(
        new ProposalPiece(
            UUID.randomUUID(),
            "Y",
            new BigDecimal("60"),
            PieceState.UNKNOWN,
            "REMAINING_LENGTH_UNVERIFIED",
            0L));
    List<ProposalLot> lots =
        List.of(
            new ProposalLot(
                p1, "P1", null, PrimaryMeasure.LENGTH, "M", WidthEvidence.NOT_REQUIRED, pieces));

    QuantityEvaluationResult result =
        QuantityEvaluator.evaluate(input("500", lots, List.of(), List.of(), false, null)).result();

    assertThat(result.unknownPieces()).isEqualTo(2);
    assertThat(result.unknownReasons()).contains("MEASURE_MISSING", "REMAINING_LENGTH_UNVERIFIED");
    assertThat(result.options())
        .allSatisfy(option -> assertThat(option.lots().getFirst().exhausted()).isFalse());
  }

  @Test
  void s09_withoutAnyEligiblePieceAndWithUnknownOnesTheStatusIsUnknownNotNone() {
    List<ProposalPiece> pieces =
        List.of(
            new ProposalPiece(
                UUID.randomUUID(), "X", null, PieceState.UNKNOWN, "WIDTH_NOT_MEASURED", 0L));
    List<ProposalLot> lots =
        List.of(
            new ProposalLot(
                p1, "P1", null, PrimaryMeasure.LENGTH, "M", WidthEvidence.UNKNOWN, pieces));

    QuantityEvaluationResult result =
        QuantityEvaluator.evaluate(input("500", lots, List.of(), List.of(), false, null)).result();

    assertThat(result.status()).isEqualTo(EvaluationStatus.UNKNOWN);
    assertThat(result.options()).isEmpty();
  }

  @Test
  void anExactWholePieceMatchIsReportedAsExact() {
    List<ProposalLot> lots = List.of(lot(p1, "P1", 250, 250));

    QuantityEvaluationResult result =
        QuantityEvaluator.evaluate(input("500", lots, List.of(), List.of(), false, null)).result();

    assertThat(result.status()).isEqualTo(EvaluationStatus.EXACT);
    assertThat(result.options())
        .singleElement()
        .extracting(QuantityOption::kind)
        .isEqualTo(OptionKind.EXACT);
  }

  @Test
  void a02b_ageOrdersLotsOnlyWhenEveryLotHasAProductionDate() {
    ProposalLot dated =
        new ProposalLot(
            p2,
            "P2",
            Instant.parse("2026-01-01T00:00:00Z"),
            PrimaryMeasure.LENGTH,
            "M",
            WidthEvidence.NOT_REQUIRED,
            eligible(100));
    ProposalLot undated =
        new ProposalLot(
            p1, "P1", null, PrimaryMeasure.LENGTH, "M", WidthEvidence.NOT_REQUIRED, eligible(100));
    ProposalLot older =
        new ProposalLot(
            new UUID(0, 9),
            "P9",
            Instant.parse("2025-01-01T00:00:00Z"),
            PrimaryMeasure.LENGTH,
            "M",
            WidthEvidence.NOT_REQUIRED,
            eligible(100));

    assertThat(QuantityEvaluator.orderedLots(List.of(dated, undated)))
        .extracting(ProposalLot::batchId)
        .containsExactly(p1, p2);
    assertThat(QuantityEvaluator.orderedLots(List.of(dated, older)))
        .extracting(ProposalLot::batchId)
        .containsExactly(older.batchId(), p2);
  }

  @Test
  void theFingerprintChangesWhenAPieceChanges() {
    List<ProposalLot> before = List.of(lot(p1, "P1", 100, 100, 100));
    List<ProposalLot> after = List.of(lot(p1, "P1", 100, 100, 99));

    String a =
        QuantityEvaluator.evaluate(input("200", before, List.of(), List.of(), false, null))
            .evidenceFingerprint();
    String b =
        QuantityEvaluator.evaluate(input("200", after, List.of(), List.of(), false, null))
            .evidenceFingerprint();

    assertThat(a).hasSize(64).isNotEqualTo(b);
  }

  private static QuantityOption option(QuantityEvaluationResult result, OptionKind kind) {
    return result.options().stream()
        .filter(option -> option.kind() == kind)
        .filter(option -> option.compatibility() != Compatibility.PENDING_CONFIRMATION)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No " + kind + " option in " + result.options()));
  }

  private static QuantityEvaluator.Input input(
      String requested,
      List<ProposalLot> lots,
      List<Set<UUID>> confirmed,
      List<Set<UUID>> tone,
      boolean singleLot,
      BigDecimal upTolerance) {
    return new QuantityEvaluator.Input(
        new BigDecimal(requested),
        "M",
        "M",
        new BigDecimal(requested),
        Function.identity(),
        lots,
        confirmed,
        tone,
        singleLot,
        upTolerance,
        null);
  }

  private ProposalLot lot(UUID id, String lotNo, double... metres) {
    return new ProposalLot(
        id, lotNo, null, PrimaryMeasure.LENGTH, "M", WidthEvidence.NOT_REQUIRED, eligible(metres));
  }

  private static List<ProposalPiece> eligible(double... metres) {
    List<ProposalPiece> pieces = new ArrayList<>();
    for (double length : metres) {
      pieces.add(
          new ProposalPiece(
              UUID.randomUUID(),
              "R" + pieces.size(),
              BigDecimal.valueOf(length),
              PieceState.ELIGIBLE,
              null,
              0L));
    }
    return pieces;
  }
}
