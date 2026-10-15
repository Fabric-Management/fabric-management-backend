package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.inventory.lothealth.domain.LotHealthPolicy;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
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
 * Turns production stock evidence into whole-piece quantity options (SOI R08–R11, A01, A04, IK-07;
 * STOCK-PROPOSAL-2). Pure: no persistence, no clock, no security. The caller converts units and
 * reads what quotes hold.
 *
 * <p>Compatibility and the single-lot requirement are prerequisites, and no computed option leaves
 * a lot with fewer normal pieces than its lot-health minimum (Fatih, 2026-10-13 and 2026-10-14): a
 * lot is closed or keeps at least that many, three by default; a piece under 51% of the lot's
 * average piece is short and does not count, and short pieces go into an order first. Among
 * candidates the tenant comes first ({@link ProposalRanking}): a remnant lot closed, more lots
 * closed, completing from the lot with fewer pieces, older stock, fewer lots, then closeness. The
 * proposal opens at (Fatih, 2026-10-13):
 *
 * <ol>
 *   <li>the best free option meeting the request within the tolerance (the request up to the upper
 *       limit), searched exactly over every combination;
 *   <li>otherwise the nearest free option above the request, outside the tolerance;
 *   <li>otherwise the best free option below the request: within the tolerance by the ranking, else
 *       the nearest below;
 *   <li>otherwise held stock, which is never applicable.
 * </ol>
 *
 * <p>Stock others hold is evaluated only when free stock cannot reach the lower tolerance limit.
 * Beside the opening one, each card the 14 October decision names is listed once: the best below
 * the request, the nearest above it outside the tolerance, nearer pending combinations and held
 * stock. Options outside the tolerance are shown, never withheld. Options are returned in the
 * decided card order (Fatih, 2026-10-14): the salesperson's changed pieces, the opening, above
 * outside the tolerance, below, pending, held stock; with the opening option's index. Both
 * tolerance limits are required: a missing side is missing information, never read as 0%; the
 * caller refuses to evaluate without them.
 *
 * <p>This class is the entry point and the input; the work is split by concern: {@link
 * ProposalEvaluation} searches and lists, {@link OptionBuilder} builds options, {@link
 * HeldStockView} reads holds, {@link ProposalReasons} words the facts and {@link
 * EvidenceFingerprint} fingerprints the evidence.
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
      List<UUID> manualPieceIds) {

    public Input {
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
    return evaluate(input, LotHealthPolicy.STANDARD);
  }

  /**
   * Evaluates with the tenant's lot-health rule (Fatih, 2026-10-14): a computed option closes a lot
   * or leaves it at least {@link LotHealthPolicy#minHealthyPieces()} normal pieces.
   */
  public static Evaluation evaluate(Input input, LotHealthPolicy lotHealth) {
    Objects.requireNonNull(lotHealth, "lotHealth");
    List<ProposalLot> lots = orderedLots(input.lots());
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
              aged),
          fingerprint);
    }
    EvaluationContext context = EvaluationContext.of(input, lots, lotHealth, aged);
    return new ProposalEvaluation(context, counts, fingerprint).run();
  }

  /**
   * Lots by age: known production dates oldest first, lots without a date after them (an unknown
   * date is never counted as old), then by identity.
   */
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
