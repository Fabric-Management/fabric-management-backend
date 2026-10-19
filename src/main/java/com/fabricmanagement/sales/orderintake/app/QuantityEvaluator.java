package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.inventory.lothealth.domain.LotHealthPolicy;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalPolicyCode;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityCoverage;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult.EvaluationStatus;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pure whole-piece proposal entry point: the caller supplies canonical measurements, package and
 * product policy evidence, quote holds and both tolerance limits. Unknown evidence stays unknown.
 *
 * <p>FABRIC_ROLL_BASE cleans remnant free pools and finds their minimum healthy completion, even
 * outside tolerance; the greatest legal quantity below the request follows. STANDARD retains the
 * existing window-based cards for other products. Single-lot is a strict constraint in either
 * policy. Only manual picks may leave an unhealthy remainder, with acknowledgement.
 *
 * <p>{@link ProposalEvaluation} searches and places cards; {@link OptionBuilder} describes their
 * quantities and closure evidence; {@link EvidenceFingerprint} captures the input evidence.
 */
public final class QuantityEvaluator {

  /** Search resolution: 0.001 of the canonical unit (millimetre or gram). */
  static final BigDecimal UNITS_PER_CANONICAL = new BigDecimal("1000");

  private QuantityEvaluator() {}

  /**
   * Everything the evaluation needs, already in the canonical unit. Tolerances are percentages and
   * both are required; 0 explicitly accepts no deviation on that side. {@code manualPieceIds} is
   * the salesperson's own pick, evaluated as one more option with the same checks.
   */
  public record Input(
      BigDecimal requestedQuantity,
      String unit,
      String canonicalUnit,
      BigDecimal canonicalTarget,
      Function<BigDecimal, BigDecimal> toLineUnit,
      List<ProposalLot> lots,
      List<Set<UUID>> confirmedGroups,
      List<Set<UUID>> toneAcceptedGroups,
      boolean singleLotRequired,
      BigDecimal agreedToleranceUpPct,
      BigDecimal agreedToleranceDownPct,
      StockHolds holds,
      List<UUID> manualPieceIds,
      ProposalPolicyCode policyCode) {

    public Input(
        BigDecimal requestedQuantity,
        String unit,
        String canonicalUnit,
        BigDecimal canonicalTarget,
        Function<BigDecimal, BigDecimal> toLineUnit,
        List<ProposalLot> lots,
        List<Set<UUID>> confirmedGroups,
        List<Set<UUID>> toneAcceptedGroups,
        boolean singleLotRequired,
        BigDecimal agreedToleranceUpPct,
        BigDecimal agreedToleranceDownPct,
        StockHolds holds,
        List<UUID> manualPieceIds) {
      this(
          requestedQuantity,
          unit,
          canonicalUnit,
          canonicalTarget,
          toLineUnit,
          lots,
          confirmedGroups,
          toneAcceptedGroups,
          singleLotRequired,
          agreedToleranceUpPct,
          agreedToleranceDownPct,
          holds,
          manualPieceIds,
          ProposalPolicyCode.STANDARD);
    }

    public boolean fabricRollPolicy() {
      return policyCode == ProposalPolicyCode.FABRIC_ROLL_BASE;
    }

    public Input {
      policyCode = policyCode == null ? ProposalPolicyCode.STANDARD : policyCode;
      confirmedGroups = confirmedGroups == null ? List.of() : List.copyOf(confirmedGroups);
      toneAcceptedGroups = toneAcceptedGroups == null ? List.of() : List.copyOf(toneAcceptedGroups);
      holds = holds == null ? StockHolds.NONE : holds;
      manualPieceIds =
          manualPieceIds == null ? List.of() : manualPieceIds.stream().distinct().toList();
      if (agreedToleranceUpPct == null || agreedToleranceDownPct == null) {
        throw new IllegalArgumentException(
            "Both tolerance limits are required; a missing side is never read as 0%");
      }
    }
  }

  /**
   * What quotes hold on the line's lots. A piece hold names its owner; a lot carries its active
   * quantity intents.
   */
  public record StockHolds(Map<UUID, Holder> pieceHolds, Map<UUID, LotIntents> lotIntents) {
    public static final StockHolds NONE = new StockHolds(Map.of(), Map.of());

    public StockHolds {
      pieceHolds = pieceHolds == null ? Map.of() : Map.copyOf(pieceHolds);
      lotIntents = lotIntents == null ? Map.of() : Map.copyOf(lotIntents);
    }
  }

  /**
   * One hold owner. Unverified: no live quote intent backs the hold record, so no quote is named.
   */
  public record Holder(
      UUID quoteId,
      UUID quoteLineId,
      String quoteNumber,
      String marketerName,
      LocalDate expiresAt,
      boolean verified) {

    static Holder unverified() {
      return new Holder(null, null, null, null, null, false);
    }
  }

  /**
   * Active quote intents on one lot; {@code canonicalTotal} is null when any of them cannot be
   * stated in the canonical unit.
   */
  public record LotIntents(List<Intent> intents, BigDecimal canonicalTotal) {
    public LotIntents {
      intents = intents == null ? List.of() : List.copyOf(intents);
    }
  }

  /** One intent; {@code canonical} is null when it cannot be stated in the canonical unit. */
  public record Intent(Holder holder, BigDecimal quantity, String unit, BigDecimal canonical) {}

  /**
   * The result, the fingerprint of the evidence it rests on and the picked pieces that are not
   * offerable stock (then no manual option is built).
   */
  public record Evaluation(
      QuantityEvaluationResult result,
      String evidenceFingerprint,
      List<UUID> manualUnavailablePieceIds) {

    public Evaluation {
      manualUnavailablePieceIds =
          manualUnavailablePieceIds == null ? List.of() : List.copyOf(manualUnavailablePieceIds);
    }

    public Evaluation(QuantityEvaluationResult result, String evidenceFingerprint) {
      this(result, evidenceFingerprint, List.of());
    }
  }

  /** Evaluates with the standard lot-health rule: a lot is closed or keeps three pieces. */
  public static Evaluation evaluate(Input input) {
    return evaluate(
        input,
        input.fabricRollPolicy()
            ? new LotHealthPolicy(4, LotHealthPolicy.STANDARD.shortPieceRatio())
            : LotHealthPolicy.STANDARD);
  }

  /**
   * Evaluates with the tenant's lot-health rule (Fatih, 2026-10-14): a computed option closes a lot
   * or leaves it at least {@link LotHealthPolicy#minHealthyPieces()} normal pieces.
   */
  public static Evaluation evaluate(Input input, LotHealthPolicy lotHealth) {
    Objects.requireNonNull(lotHealth, "lotHealth");
    List<ProposalLot> lots =
        orderedLots(input.fabricRollPolicy() ? fabricLots(input.lots()) : input.lots());
    String fingerprint = EvidenceFingerprint.of(input, lots, lotHealth);
    Counts counts = Counts.of(lots);
    boolean aged = allDated(lots);
    boolean precise =
        representable(input.canonicalTarget())
            && lots.stream()
                .flatMap(lot -> lot.pieces().stream())
                .filter(piece -> piece.state() == PieceState.ELIGIBLE)
                .allMatch(piece -> representable(piece.canonicalMeasure()));
    if (!precise || toUnits(input.canonicalTarget()) <= 0) {
      return new Evaluation(
          result(
                  input,
                  EvaluationStatus.UNKNOWN,
                  List.of(),
                  null,
                  true,
                  counts,
                  List.of("QUANTITY_BELOW_RESOLUTION"),
                  aged)
              .withPolicy(
                  input.policyCode(),
                  lotHealth.minHealthyPieces(),
                  new QuantityCoverage(
                      QuantityCoverage.Status.UNKNOWN,
                      null,
                      List.of(QuantityCoverage.Reason.QUANTITY_RESOLUTION_UNSUPPORTED))),
          fingerprint);
    }
    try {
      EvaluationContext context = EvaluationContext.of(input, lots, lotHealth, aged);
      return new ProposalEvaluation(context, counts, fingerprint).run();
    } catch (ArithmeticException outsideSearchRange) {
      return new Evaluation(
          result(
                  input,
                  EvaluationStatus.UNKNOWN,
                  List.of(),
                  null,
                  false,
                  counts,
                  List.of("QUANTITY_OUTSIDE_SEARCH_RANGE"),
                  aged)
              .withPolicy(
                  input.policyCode(),
                  lotHealth.minHealthyPieces(),
                  new QuantityCoverage(
                      QuantityCoverage.Status.UNKNOWN,
                      null,
                      List.of(QuantityCoverage.Reason.QUANTITY_RESOLUTION_UNSUPPORTED))),
          fingerprint);
    }
  }

  /** Package evidence is required; units and barcodes cannot imply a fabric roll. */
  private static List<ProposalLot> fabricLots(List<ProposalLot> lots) {
    return lots.stream()
        .map(
            lot ->
                new ProposalLot(
                    lot.batchId(),
                    lot.lotNo(),
                    lot.productionDate(),
                    lot.measure(),
                    lot.canonicalUnit(),
                    lot.widthEvidence(),
                    lot.pieces().stream()
                        .map(
                            piece ->
                                lot.measure()
                                            == com.fabricmanagement.production.core.batch.domain
                                                .PrimaryMeasure.LENGTH
                                        && piece.packageType()
                                            == com.fabricmanagement.production.core.stockunit.domain
                                                .PackageType.ROLL
                                    ? piece
                                    : new ProposalPiece(
                                        piece.stockUnitId(),
                                        piece.pieceNo(),
                                        piece.canonicalMeasure(),
                                        PieceState.EXCLUDED,
                                        "UNSUPPORTED_PACKAGE",
                                        piece.version(),
                                        piece.packageType(),
                                        piece.initialCanonicalMeasure()))
                        .toList(),
                    lot.averagePiece(),
                    lot.averageBasis(),
                    lot.inventoryComplete()))
        .toList();
  }

  /** Known production dates oldest first; unknown dates last, then stable identity. */
  static List<ProposalLot> orderedLots(List<ProposalLot> lots) {
    Comparator<ProposalLot> order =
        Comparator.comparing(
                ProposalLot::productionDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ProposalLot::batchId);
    return lots.stream().sorted(order).toList();
  }

  /** Every lot with offerable pieces has a production date, and there is more than one. */
  private static boolean allDated(List<ProposalLot> lots) {
    List<ProposalLot> offerable = lots.stream().filter(QuantityEvaluator::hasEligible).toList();
    return offerable.size() > 1 && offerable.stream().allMatch(lot -> lot.productionDate() != null);
  }

  static boolean hasEligible(ProposalLot lot) {
    return lot.pieces().stream().anyMatch(piece -> piece.state() == PieceState.ELIGIBLE);
  }

  /** How many pieces are offerable, of unknown state or excluded, and why some are unknown. */
  record Counts(int eligible, int unknown, int excluded, Set<String> unknownReasons) {
    static Counts of(List<ProposalLot> lots) {
      int eligible = 0;
      int unknown = 0;
      int excluded = 0;
      Set<String> reasons = new TreeSet<>();
      for (ProposalLot lot : lots) {
        for (ProposalPiece piece : lot.pieces()) {
          switch (piece.state()) {
            case ELIGIBLE -> eligible++;
            case UNKNOWN -> {
              unknown++;
              if (piece.reason() != null) {
                reasons.add(piece.reason());
              }
            }
            case EXCLUDED -> excluded++;
          }
        }
      }
      return new Counts(eligible, unknown, excluded, reasons);
    }
  }

  static QuantityEvaluationResult result(
      Input input,
      EvaluationStatus status,
      List<QuantityOption> options,
      Integer mainIndex,
      boolean searchComplete,
      Counts counts,
      List<String> reasons,
      boolean aged) {
    return new QuantityEvaluationResult(
        status,
        input.requestedQuantity(),
        input.unit(),
        input.canonicalUnit(),
        options,
        mainIndex,
        searchComplete,
        counts.eligible(),
        counts.unknown(),
        counts.excluded(),
        reasons,
        aged);
  }

  private static boolean representable(BigDecimal quantity) {
    if (quantity == null || quantity.signum() <= 0) return false;
    try {
      toUnits(quantity);
      return true;
    } catch (ArithmeticException e) {
      return false;
    }
  }

  static long toUnits(BigDecimal canonical) {
    if (canonical == null) {
      return 0;
    }
    return canonical.multiply(UNITS_PER_CANONICAL).longValueExact();
  }

  /** A capacity in search units, rounded down so it never allows more than the free quantity. */
  static long floorUnits(BigDecimal canonical) {
    return canonical.multiply(UNITS_PER_CANONICAL).setScale(0, RoundingMode.FLOOR).longValueExact();
  }

  /** SHA-256 in hex; the evaluation's fingerprints and keys use it. */
  static String sha256(String text) {
    return EvidenceFingerprint.sha256(text);
  }
}
