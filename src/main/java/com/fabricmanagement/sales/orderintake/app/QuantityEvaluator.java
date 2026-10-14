package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalReason;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalSearch;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult.EvaluationStatus;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.BlockedReason;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.Compatibility;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.HeldStock;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.HeldStockKind;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionKind;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionPlacement;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.StockSource;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.ToleranceStatus;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns production stock evidence into whole-piece quantity options (SOI R08–R11, A01, A04, IK-07;
 * STOCK-PROPOSAL-2). Pure: no persistence, no clock, no security. The caller converts units and
 * reads what quotes hold.
 *
 * <p>Compatibility and the single-lot requirement are prerequisites, and no computed option leaves
 * one or two pieces in a lot (Fatih, 2026-10-13): a lot is closed or keeps at least three. Among
 * candidates the tenant comes first: more lots closed, then older stock, then fewer lots, then
 * closeness. The proposal opens at (Fatih, 2026-10-13):
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
 * stock. Options outside the tolerance are shown, never withheld. Options are returned in ascending
 * quantity with the opening option's index. Both tolerance limits are required: a missing side is
 * missing information, never read as 0%; the caller refuses to evaluate without them.
 */
public final class QuantityEvaluator {

  /** Search resolution: 0.001 of the canonical unit (millimetre or gram). */
  static final BigDecimal UNITS_PER_CANONICAL = new BigDecimal("1000");

  private static final BigDecimal HUNDRED = new BigDecimal("100");

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

  public static Evaluation evaluate(Input input) {
    List<ProposalLot> lots = orderedLots(input.lots());
    String fingerprint = fingerprint(input, lots);
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
    return new Search(input, lots, counts, aged, fingerprint).run();
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

  private static boolean hasEligible(ProposalLot lot) {
    return lot.pieces().stream().anyMatch(piece -> piece.state() == PieceState.ELIGIBLE);
  }

  private enum Tier {
    FREE,
    HELD
  }

  private record Run(List<UUID> lots, Compatibility compatibility, int order) {
    boolean definite() {
      return compatibility != Compatibility.PENDING_CONFIRMATION;
    }
  }

  /** An option with what the tenant-first ranking compares. */
  private record Ranked(
      QuantityOption option,
      boolean definite,
      Tier tier,
      int remnantPenalty,
      int closedLots,
      List<Instant> dates,
      long units,
      String pieceKey) {}

  /**
   * What one tier's stock offers across its compatible sets of lots: the best meeting the request
   * within the tolerance ({@code covering}), the nearest at or above it ({@code above}), the best
   * below it within the tolerance ({@code shortWithin}), the nearest below it ({@code below}) and
   * nearer pending combinations.
   */
  private record Found(
      List<Ranked> covering,
      List<Ranked> above,
      List<Ranked> shortWithin,
      List<Ranked> below,
      List<Ranked> pending) {

    static final Found NONE = new Found(List.of(), List.of(), List.of(), List.of(), List.of());

    /** Some option meets the request, or falls short of it within the tolerance. */
    boolean reachesLowerLimit() {
      return !covering.isEmpty() || !above.isEmpty() || !shortWithin.isEmpty();
    }

    /** Only the options that take stock others hold. */
    Found onlyHeld() {
      return new Found(held(covering), held(above), held(shortWithin), held(below), held(pending));
    }

    private static List<Ranked> held(List<Ranked> candidates) {
      return candidates.stream()
          .filter(ranked -> ranked.option().source() == StockSource.HELD)
          .toList();
    }

    /**
     * Where the proposal opens (Fatih, 2026-10-13): the best meeting the request within the
     * tolerance, otherwise the nearest above it, otherwise the best below it.
     */
    Optional<Ranked> opening(
        Comparator<Ranked> ranking, Comparator<Ranked> aboveOrder, Comparator<Ranked> belowOrder) {
      return covering.stream()
          .min(ranking)
          .or(() -> above.stream().min(aboveOrder))
          .or(() -> shortSide(ranking, belowOrder));
    }

    /** The best below the request: within the tolerance by the ranking, else the nearest below. */
    Optional<Ranked> shortSide(Comparator<Ranked> ranking, Comparator<Ranked> belowOrder) {
      return shortWithin.stream().min(ranking).or(() -> below.stream().min(belowOrder));
    }

    /**
     * The nearest at or above the request. It is a card of its own only outside the tolerance, that
     * is when nothing meets the request within it.
     */
    Optional<Ranked> nearestAbove(Comparator<Ranked> aboveOrder) {
      return above.stream().min(aboveOrder);
    }
  }

  private record Counts(int eligible, int unknown, int excluded, Set<String> unknownReasons) {
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

  /**
   * What the lot's unheld pieces may give free: unheld eligible stock less the part of quote
   * intents not already pinned to held pieces. Null when it cannot be stated.
   */
  private record LotFree(
      BigDecimal canonical, boolean unconvertibleIntent, boolean unverifiedHold, boolean intents) {
    boolean known() {
      return canonical != null;
    }
  }

  /** One evaluation's working state. */
  private static final class Search {
    private final Input input;
    private final List<ProposalLot> lots;
    private final Counts counts;
    private final boolean aged;
    private final String fingerprint;
    private final long target;
    private final Map<UUID, ProposalLot> lotsById;
    private final Map<UUID, ProposalPiece> piecesById = new LinkedHashMap<>();
    private final Map<UUID, UUID> lotOfPiece = new LinkedHashMap<>();
    private final Map<UUID, LotFree> free = new LinkedHashMap<>();
    private boolean limitExceeded;
    private final long windowLow;
    private final long windowHigh;
    private final Map<UUID, Integer> ageRanks = new LinkedHashMap<>();

    /**
     * The tenant-first ranking: prerequisites met, free stock, no remnant (only a manual pick can
     * leave one), more lots taken whole, older stock, fewer lots, closer to the request, covering
     * the request, then the pieces themselves.
     */
    private final Comparator<Ranked> tenantFirst;

    /** Ascending quantity; same quantity by the tenant-first ranking. */
    private final Comparator<Ranked> listedOrder;

    /** The nearest at or above the request first, then the ranking. */
    private final Comparator<Ranked> aboveOrder;

    /** The nearest below the request first, then the ranking. */
    private final Comparator<Ranked> belowOrder;

    Search(Input input, List<ProposalLot> lots, Counts counts, boolean aged, String fingerprint) {
      this.input = input;
      this.lots = lots;
      this.counts = counts;
      this.aged = aged;
      this.fingerprint = fingerprint;
      this.target = toUnits(input.canonicalTarget());
      // Built after the target: a field initialiser would run before it is assigned.
      this.tenantFirst = tenantFirstFor(target);
      this.listedOrder = Comparator.comparingLong(Ranked::units).thenComparing(tenantFirst);
      this.aboveOrder = Comparator.comparingLong(Ranked::units).thenComparing(tenantFirst);
      this.belowOrder =
          Comparator.comparingLong(Ranked::units).reversed().thenComparing(tenantFirst);
      this.lotsById = new LinkedHashMap<>();
      for (ProposalLot lot : lots) {
        lotsById.put(lot.batchId(), lot);
        for (ProposalPiece piece : lot.pieces()) {
          piecesById.put(piece.stockUnitId(), piece);
          lotOfPiece.put(piece.stockUnitId(), lot.batchId());
        }
        free.put(lot.batchId(), lotFree(lot));
      }
      // The agreed tolerance as a window of search units around the target.
      BigDecimal units = BigDecimal.valueOf(target);
      this.windowHigh =
          target
              + units
                  .multiply(input.agreedToleranceUpPct())
                  .divide(HUNDRED, 0, RoundingMode.FLOOR)
                  .longValueExact();
      this.windowLow =
          Math.max(
              1,
              target
                  - units
                      .multiply(input.agreedToleranceDownPct())
                      .divide(HUNDRED, 0, RoundingMode.FLOOR)
                      .longValueExact());
      List<Instant> dates =
          lots.stream()
              .map(ProposalLot::productionDate)
              .filter(Objects::nonNull)
              .distinct()
              .sorted()
              .toList();
      for (ProposalLot lot : lots) {
        ageRanks.put(
            lot.batchId(),
            lot.productionDate() == null ? Integer.MAX_VALUE : dates.indexOf(lot.productionDate()));
      }
    }

    private static Comparator<Ranked> tenantFirstFor(long target) {
      return Comparator.comparing((Ranked ranked) -> !ranked.definite())
          .thenComparing(Ranked::tier)
          .thenComparingInt(Ranked::remnantPenalty)
          .thenComparing(Comparator.comparingInt(Ranked::closedLots).reversed())
          .thenComparing(Ranked::dates, QuantityEvaluator::compareAges)
          .thenComparingInt(ranked -> ranked.option().lotCount())
          .thenComparingLong(ranked -> Math.abs(ranked.units() - target))
          .thenComparing(ranked -> ranked.units() < target)
          .thenComparing(Ranked::pieceKey);
    }

    Evaluation run() {
      List<Run> runs = runs();
      Found freeOptions = candidates(Tier.FREE, runs);
      // Held stock only when free stock cannot reach the lower tolerance limit (Fatih,
      // 2026-10-13); then it is an option to weigh, never to apply.
      Found heldOptions =
          freeOptions.reachesLowerLimit() ? Found.NONE : candidates(Tier.HELD, runs).onlyHeld();

      Optional<Ranked> freeOpening = freeOptions.opening(tenantFirst, aboveOrder, belowOrder);
      Optional<Ranked> heldOpening = heldOptions.opening(tenantFirst, aboveOrder, belowOrder);
      Optional<Ranked> opening = freeOpening.or(() -> heldOpening);

      List<Ranked> chosen = new ArrayList<>();
      opening.ifPresent(chosen::add);
      // Beside it, one card of each kind (Fatih, 2026-10-14): the best below the request and the
      // nearest above it outside the tolerance; a second option within the tolerance is not a card.
      freeOptions.shortSide(tenantFirst, belowOrder).ifPresent(chosen::add);
      freeOptions
          .nearestAbove(aboveOrder)
          .filter(ranked -> ranked.units() > windowHigh)
          .ifPresent(chosen::add);
      chosen.addAll(freeOptions.pending());
      heldOpening.ifPresent(chosen::add);
      heldOptions.shortSide(tenantFirst, belowOrder).ifPresent(chosen::add);

      // The same pieces are listed once; the opening placement wins.
      Map<String, Ranked> unique = new LinkedHashMap<>();
      for (Ranked ranked : chosen) {
        unique.putIfAbsent(ranked.pieceKey(), ranked);
      }
      List<Ranked> listed = new ArrayList<>();
      String openingKey = opening.map(Ranked::pieceKey).orElse(null);
      for (Ranked ranked : unique.values()) {
        OptionPlacement placement =
            ranked.pieceKey().equals(openingKey)
                ? OptionPlacement.MAIN
                : ranked.units() < target ? OptionPlacement.BELOW : OptionPlacement.ABOVE;
        listed.add(withOption(ranked, ranked.option().as(placement)));
      }

      List<UUID> unavailable = List.of();
      if (!input.manualPieceIds().isEmpty()) {
        unavailable = unavailableManualPieces();
        if (unavailable.isEmpty()) {
          listed.add(manual());
        }
      }

      listed.sort(listedOrder);
      List<QuantityOption> options = withReasons(listed.stream().map(Ranked::option).toList());
      Integer mainIndex = null;
      for (int i = 0; i < options.size(); i++) {
        if (options.get(i).placement() == OptionPlacement.MAIN) {
          mainIndex = i;
        }
      }

      EvaluationStatus status;
      if (mainIndex != null
          && options.get(mainIndex).difference().signum() == 0
          && !options.get(mainIndex).needsRemnantAcknowledgement()) {
        status = EvaluationStatus.EXACT;
      } else if (!options.isEmpty()) {
        status = EvaluationStatus.OPTIONS;
      } else if (counts.unknown() > 0 || limitExceeded) {
        status = EvaluationStatus.UNKNOWN;
      } else {
        status = EvaluationStatus.NO_ELIGIBLE_STOCK;
      }
      Set<String> reasons = new TreeSet<>(counts.unknownReasons());
      if (limitExceeded) {
        reasons.add("EVALUATION_LIMIT");
      }
      return new Evaluation(
          result(
              input,
              status,
              options,
              mainIndex,
              !limitExceeded,
              counts,
              List.copyOf(reasons),
              aged),
          fingerprint,
          unavailable);
    }

    // ---------------------------------------------------------------- candidates

    private List<Run> runs() {
      List<Run> runs = new ArrayList<>();
      List<UUID> ordered = lots.stream().map(ProposalLot::batchId).toList();
      for (UUID lot : ordered) {
        runs.add(new Run(List.of(lot), Compatibility.SINGLE_LOT, runs.size()));
      }
      if (input.singleLotRequired() || ordered.size() < 2) {
        return runs;
      }
      for (Set<UUID> group : input.confirmedGroups()) {
        List<UUID> members = ordered.stream().filter(group::contains).toList();
        if (members.size() >= 2) {
          runs.add(new Run(members, Compatibility.CONFIRMED, runs.size()));
        }
      }
      for (Set<UUID> group : input.toneAcceptedGroups()) {
        List<UUID> members = ordered.stream().filter(group::contains).toList();
        if (members.size() >= 2) {
          runs.add(new Run(members, Compatibility.TONE_ACCEPTED, runs.size()));
        }
      }
      runs.add(new Run(ordered, Compatibility.PENDING_CONFIRMATION, runs.size()));
      return runs;
    }

    /**
     * What one tier's stock offers, per compatible set of lots. Definite runs (single lot,
     * confirmed group, tone acceptance) give: the best meeting the request within the tolerance,
     * the nearest at or above it, the best below it within the tolerance and the nearest below it.
     * A pending combination is kept only when it uses several lots no definite group covers and is
     * nearer to the request than the definite options on its side (SOI S05). No computed selection
     * leaves one or two pieces in a lot.
     */
    private Found candidates(Tier tier, List<Run> runs) {
      Map<UUID, WholePieceSearch.Lot> pools = pools(tier);
      List<Ranked> covering = new ArrayList<>();
      List<Ranked> above = new ArrayList<>();
      List<Ranked> shortWithin = new ArrayList<>();
      List<Ranked> below = new ArrayList<>();
      List<Ranked> pendingAbove = new ArrayList<>();
      List<Ranked> pendingBelow = new ArrayList<>();
      for (Run run : runs) {
        List<ProposalSearch.SearchLot> runLots =
            run.lots().stream()
                .map(pools::get)
                .filter(Objects::nonNull)
                .filter(lot -> !lot.pieces().isEmpty())
                .map(lot -> new ProposalSearch.SearchLot(lot, ageRanks.get(lot.id())))
                .toList();
        if (runLots.isEmpty()) {
          continue;
        }
        if (run.definite()) {
          found(ProposalSearch.bestWithin(runLots, target, windowHigh, target))
              .ifPresent(selection -> covering.add(ranked(run, selection)));
          found(ProposalSearch.smallestFrom(runLots, target))
              .ifPresent(selection -> above.add(ranked(run, selection)));
          if (windowLow < target) {
            found(ProposalSearch.bestWithin(runLots, windowLow, target - 1, target))
                .ifPresent(selection -> shortWithin.add(ranked(run, selection)));
          }
          found(ProposalSearch.largestUpTo(runLots, target - 1))
              .ifPresent(selection -> below.add(ranked(run, selection)));
        } else {
          found(ProposalSearch.smallestFrom(runLots, target))
              .filter(selection -> severalUncoveredLots(selection, runs))
              .ifPresent(selection -> pendingAbove.add(ranked(run, selection)));
          found(ProposalSearch.largestUpTo(runLots, target - 1))
              .filter(selection -> severalUncoveredLots(selection, runs))
              .ifPresent(selection -> pendingBelow.add(ranked(run, selection)));
        }
      }
      List<Ranked> pending = new ArrayList<>();
      Optional<Ranked> definiteAbove = above.stream().min(aboveOrder);
      pendingAbove.stream()
          .min(aboveOrder)
          .filter(
              candidate ->
                  definiteAbove.isEmpty() || candidate.units() < definiteAbove.get().units())
          .ifPresent(pending::add);
      Optional<Ranked> definiteBelow = below.stream().min(belowOrder);
      pendingBelow.stream()
          .min(belowOrder)
          .filter(
              candidate ->
                  definiteBelow.isEmpty() || candidate.units() > definiteBelow.get().units())
          .ifPresent(pending::add);
      return new Found(covering, above, shortWithin, below, pending);
    }

    private Optional<WholePieceSearch.Selection> found(ProposalSearch.Result result) {
      limitExceeded |= result.limitExceeded();
      return result.best();
    }

    private boolean severalUncoveredLots(WholePieceSearch.Selection selection, List<Run> runs) {
      Set<UUID> used =
          selection.takes().stream()
              .map(WholePieceSearch.LotTake::lotId)
              .collect(Collectors.toSet());
      return used.size() >= 2
          && runs.stream().filter(Run::definite).noneMatch(run -> run.lots().containsAll(used));
    }

    /**
     * The pieces each lot offers to a tier. Free: eligible pieces without a hold, bounded by the
     * lot's free quantity; a lot whose free quantity cannot be stated offers nothing free. Held:
     * every eligible piece.
     */
    private Map<UUID, WholePieceSearch.Lot> pools(Tier tier) {
      Map<UUID, WholePieceSearch.Lot> pools = new LinkedHashMap<>();
      for (ProposalLot lot : lots) {
        LotFree lotFree = free.get(lot.batchId());
        List<WholePieceSearch.Piece> pieces;
        long capacity = WholePieceSearch.Lot.UNLIMITED;
        if (tier == Tier.FREE) {
          if (!lotFree.known()) {
            pieces = List.of();
          } else {
            pieces = poolPieces(lot, true);
            capacity = floorUnits(lotFree.canonical());
          }
        } else {
          pieces = poolPieces(lot, false);
        }
        pools.put(lot.batchId(), new WholePieceSearch.Lot(lot.batchId(), pieces, capacity));
      }
      return pools;
    }

    private List<WholePieceSearch.Piece> poolPieces(ProposalLot lot, boolean freeOnly) {
      return lot.pieces().stream()
          .filter(piece -> piece.state() == PieceState.ELIGIBLE)
          .filter(
              piece -> !freeOnly || !input.holds().pieceHolds().containsKey(piece.stockUnitId()))
          .map(
              piece ->
                  new WholePieceSearch.Piece(
                      piece.stockUnitId(), toUnits(piece.canonicalMeasure())))
          .filter(piece -> piece.units() > 0)
          .toList();
    }

    private LotFree lotFree(ProposalLot lot) {
      LotIntents intents = input.holds().lotIntents().get(lot.batchId());
      boolean hasIntents = intents != null && !intents.intents().isEmpty();
      boolean unconvertible =
          hasIntents && intents.intents().stream().anyMatch(intent -> intent.canonical() == null);
      List<ProposalPiece> eligible =
          lot.pieces().stream().filter(piece -> piece.state() == PieceState.ELIGIBLE).toList();
      boolean unverified =
          eligible.stream()
              .map(piece -> input.holds().pieceHolds().get(piece.stockUnitId()))
              .anyMatch(holder -> holder != null && !holder.verified());
      if (unconvertible || unverified) {
        return new LotFree(null, unconvertible, unverified, hasIntents);
      }
      BigDecimal unheld = BigDecimal.ZERO;
      Map<UUID, BigDecimal> pinned = new LinkedHashMap<>();
      for (ProposalPiece piece : eligible) {
        Holder holder = input.holds().pieceHolds().get(piece.stockUnitId());
        if (holder == null) {
          unheld = unheld.add(piece.canonicalMeasure());
        } else {
          pinned.merge(holder.quoteLineId(), piece.canonicalMeasure(), BigDecimal::add);
        }
      }
      Map<UUID, BigDecimal> intended = new LinkedHashMap<>();
      if (hasIntents) {
        intents
            .intents()
            .forEach(
                intent ->
                    intended.merge(
                        intent.holder().quoteLineId(), intent.canonical(), BigDecimal::add));
      }
      BigDecimal uncovered = BigDecimal.ZERO;
      for (Map.Entry<UUID, BigDecimal> line : intended.entrySet()) {
        BigDecimal open =
            line.getValue().subtract(pinned.getOrDefault(line.getKey(), BigDecimal.ZERO));
        uncovered = uncovered.add(open.max(BigDecimal.ZERO));
      }
      return new LotFree(unheld.subtract(uncovered).max(BigDecimal.ZERO), false, false, hasIntents);
    }

    // ---------------------------------------------------------------- options

    private Ranked ranked(Run run, WholePieceSearch.Selection selection) {
      QuantityOption option =
          option(
              "S:" + pieceKey(selection.takes()),
              selection.takes(),
              run.compatibility(),
              run.definite() ? null : BlockedReason.COMPATIBILITY_CONFIRMATION_REQUIRED);
      return rank(option, run.definite(), selection.takes());
    }

    private Ranked rank(
        QuantityOption option, boolean definite, List<WholePieceSearch.LotTake> takes) {
      // Only a manual pick can leave a remnant; computed selections never do.
      int penalty = option.leavesRemnant() ? 1 : 0;
      int closed = (int) takes.stream().filter(take -> take.remainingPieces() == 0).count();
      List<Instant> dates =
          option.lots().stream()
              .map(QuantityOption.LotPart::productionDate)
              .sorted(Comparator.nullsLast(Comparator.naturalOrder()))
              .toList();
      return new Ranked(
          option,
          definite,
          option.source() == StockSource.FREE ? Tier.FREE : Tier.HELD,
          penalty,
          closed,
          dates,
          takes.stream().mapToLong(WholePieceSearch.LotTake::units).sum(),
          pieceKey(takes));
    }

    /**
     * Builds an option from lot takes: quantities, tolerance, lot parts and what other quotes hold
     * on it. {@code compatibilityBlock} is the prerequisite block, if any.
     */
    private QuantityOption option(
        String key,
        List<WholePieceSearch.LotTake> takes,
        Compatibility compatibility,
        BlockedReason compatibilityBlock) {
      List<QuantityOption.LotPart> parts = new ArrayList<>();
      List<HeldStock> held = new ArrayList<>();
      boolean touchesVerified = false;
      boolean touchesUnverified = false;
      BigDecimal canonical = BigDecimal.ZERO;
      for (WholePieceSearch.LotTake take : takes) {
        ProposalLot lot = lotsById.get(take.lotId());
        Set<UUID> taken = Set.copyOf(take.pieceIds());
        BigDecimal lotCanonical =
            take.pieceIds().stream()
                .map(id -> piecesById.get(id).canonicalMeasure())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        canonical = canonical.add(lotCanonical);
        parts.add(part(lot, take, taken, lotCanonical));

        HeldOnLot onLot = heldOnLot(lot, taken);
        held.addAll(onLot.entries());
        touchesVerified |= onLot.verified();
        touchesUnverified |= onLot.unverified();
      }
      BigDecimal quantity = input.toLineUnit().apply(canonical);
      BigDecimal difference = quantity.subtract(input.requestedQuantity());
      BigDecimal percent =
          difference.multiply(HUNDRED).divide(input.requestedQuantity(), 1, RoundingMode.HALF_UP);
      boolean remnant = parts.stream().anyMatch(QuantityOption.LotPart::remnant);
      OptionKind kind =
          difference.signum() == 0
              ? remnant ? OptionKind.REQUESTED_WITH_REMNANT : OptionKind.EXACT
              : difference.signum() > 0 ? OptionKind.ABOVE : OptionKind.BELOW;
      BlockedReason blocked = compatibilityBlock;
      if (blocked == null && touchesVerified) {
        blocked = BlockedReason.HELD_BY_ANOTHER_QUOTE;
      } else if (blocked == null && touchesUnverified) {
        blocked = BlockedReason.HOLD_UNVERIFIED;
      }
      return new QuantityOption(
          key,
          OptionPlacement.ABOVE,
          kind,
          compatibility,
          touchesVerified || touchesUnverified ? StockSource.HELD : StockSource.FREE,
          quantity,
          canonical,
          difference,
          percent,
          tolerance(difference),
          List.of(),
          parts,
          held,
          blocked);
    }

    private QuantityOption.LotPart part(
        ProposalLot lot, WholePieceSearch.LotTake take, Set<UUID> taken, BigDecimal lotCanonical) {
      List<ProposalPiece> remainingEligible =
          lot.pieces().stream()
              .filter(piece -> piece.state() == PieceState.ELIGIBLE)
              .filter(piece -> !taken.contains(piece.stockUnitId()))
              .toList();
      BigDecimal remainingCanonical =
          remainingEligible.stream()
              .map(ProposalPiece::canonicalMeasure)
              .filter(Objects::nonNull)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      int unknownInLot =
          (int) lot.pieces().stream().filter(piece -> piece.state() == PieceState.UNKNOWN).count();
      // One or two pool pieces left (Fatih, 2026-10-13); free pieces first, as the free pool is
      // the one searched. Pieces of unknown state are never counted: they are not offered.
      boolean remnant = WholePieceSearch.isRemnant(take.remainingPieces());
      List<ProposalPiece> remnantPieces =
          !remnant
              ? List.of()
              : remainingEligible.stream()
                  .sorted(
                      Comparator.comparing(
                          (ProposalPiece piece) ->
                              input.holds().pieceHolds().containsKey(piece.stockUnitId())))
                  .limit(take.remainingPieces())
                  .toList();
      BigDecimal remnantCanonical =
          remnantPieces.stream()
              .map(ProposalPiece::canonicalMeasure)
              .filter(Objects::nonNull)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      boolean closes = lot.pieces().stream().allMatch(piece -> taken.contains(piece.stockUnitId()));
      return new QuantityOption.LotPart(
          lot.batchId(),
          lot.lotNo(),
          lot.productionDate(),
          take.pieceIds(),
          input.toLineUnit().apply(lotCanonical),
          remainingEligible.size(),
          input.toLineUnit().apply(remainingCanonical),
          unknownInLot,
          remnant,
          remnantPieces.stream().map(ProposalPiece::stockUnitId).toList(),
          remnant ? input.toLineUnit().apply(remnantCanonical) : null,
          closes);
    }

    private record HeldOnLot(List<HeldStock> entries, boolean verified, boolean unverified) {}

    /**
     * What other quotes hold that taking {@code taken} from the lot touches: held pieces it takes
     * and, when its unheld pieces exceed what the lot gives free or that cannot be stated, the
     * lot's intents and unverifiable hold records.
     */
    private HeldOnLot heldOnLot(ProposalLot lot, Set<UUID> taken) {
      List<HeldStock> entries = new ArrayList<>();
      boolean verified = false;
      boolean unverified = false;
      Map<UUID, List<UUID>> verifiedPieces = new LinkedHashMap<>();
      Map<UUID, Holder> holders = new LinkedHashMap<>();
      List<UUID> unverifiedPieces = new ArrayList<>();
      for (ProposalPiece piece : lot.pieces()) {
        if (!taken.contains(piece.stockUnitId())) {
          continue;
        }
        Holder holder = input.holds().pieceHolds().get(piece.stockUnitId());
        if (holder == null) {
          continue;
        }
        if (holder.verified()) {
          verifiedPieces
              .computeIfAbsent(holder.quoteLineId(), ignored -> new ArrayList<>())
              .add(piece.stockUnitId());
          holders.putIfAbsent(holder.quoteLineId(), holder);
          verified = true;
        } else {
          unverifiedPieces.add(piece.stockUnitId());
          unverified = true;
        }
      }
      verifiedPieces.forEach(
          (line, pieces) -> entries.add(pieceHold(lot, holders.get(line), pieces)));
      LotFree lotFree = free.get(lot.batchId());
      BigDecimal takenUnheld =
          lot.pieces().stream()
              .filter(piece -> taken.contains(piece.stockUnitId()))
              .filter(piece -> !input.holds().pieceHolds().containsKey(piece.stockUnitId()))
              .map(ProposalPiece::canonicalMeasure)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      boolean beyondFree = !lotFree.known() || takenUnheld.compareTo(lotFree.canonical()) > 0;
      if (beyondFree) {
        LotIntents intents = input.holds().lotIntents().get(lot.batchId());
        if (intents != null) {
          for (Intent intent : intents.intents()) {
            Holder holder = intent.holder();
            entries.add(
                new HeldStock(
                    lot.batchId(),
                    lot.lotNo(),
                    HeldStockKind.LOT_INTENT,
                    true,
                    holder.quoteId(),
                    holder.quoteNumber(),
                    holder.marketerName(),
                    holder.expiresAt(),
                    List.of(),
                    intent.quantity(),
                    intent.unit()));
            verified = true;
          }
        }
        if (lotFree.unverifiedHold()) {
          lot.pieces().stream()
              .filter(piece -> piece.state() == PieceState.ELIGIBLE)
              .map(ProposalPiece::stockUnitId)
              .filter(id -> !taken.contains(id))
              .filter(
                  id -> {
                    Holder holder = input.holds().pieceHolds().get(id);
                    return holder != null && !holder.verified();
                  })
              .forEach(unverifiedPieces::add);
          unverified = true;
        }
      }
      if (!unverifiedPieces.isEmpty()) {
        entries.add(pieceHold(lot, Holder.unverified(), unverifiedPieces));
      }
      return new HeldOnLot(entries, verified, unverified);
    }

    private static HeldStock pieceHold(ProposalLot lot, Holder holder, List<UUID> pieces) {
      return new HeldStock(
          lot.batchId(),
          lot.lotNo(),
          HeldStockKind.PIECE_HOLD,
          holder.verified(),
          holder.verified() ? holder.quoteId() : null,
          holder.verified() ? holder.quoteNumber() : null,
          holder.verified() ? holder.marketerName() : null,
          holder.verified() ? holder.expiresAt() : null,
          pieces,
          null,
          null);
    }

    /** Against the agreed tolerance; 0% on a side accepts no deviation on that side. */
    private ToleranceStatus tolerance(BigDecimal difference) {
      if (difference.signum() == 0) {
        return ToleranceStatus.WITHIN;
      }
      BigDecimal limit =
          difference.signum() > 0 ? input.agreedToleranceUpPct() : input.agreedToleranceDownPct();
      boolean outside =
          difference.abs().multiply(HUNDRED).compareTo(input.requestedQuantity().multiply(limit))
              > 0;
      return outside ? ToleranceStatus.OUTSIDE : ToleranceStatus.WITHIN;
    }

    // ---------------------------------------------------------------- manual pick

    private List<UUID> unavailableManualPieces() {
      return input.manualPieceIds().stream()
          .filter(
              id -> {
                ProposalPiece piece = piecesById.get(id);
                return piece == null
                    || piece.state() != PieceState.ELIGIBLE
                    || piece.canonicalMeasure() == null
                    || toUnits(piece.canonicalMeasure()) <= 0;
              })
          .toList();
    }

    /**
     * The salesperson's pick with the same checks: the single-lot requirement, lot compatibility,
     * holds and the remnant rule over the pool it was taken from (free when it fits free stock).
     */
    private Ranked manual() {
      Map<UUID, List<UUID>> byLot = new LinkedHashMap<>();
      for (UUID id : input.manualPieceIds()) {
        byLot.computeIfAbsent(lotOfPiece.get(id), ignored -> new ArrayList<>()).add(id);
      }
      Map<UUID, WholePieceSearch.Lot> freePools = pools(Tier.FREE);
      boolean fitsFree =
          byLot.entrySet().stream()
              .allMatch(
                  entry -> {
                    WholePieceSearch.Lot pool = freePools.get(entry.getKey());
                    Set<UUID> poolIds =
                        pool.pieces().stream()
                            .map(WholePieceSearch.Piece::id)
                            .collect(Collectors.toSet());
                    long units = units(entry.getValue());
                    return poolIds.containsAll(entry.getValue()) && units <= pool.capacity();
                  });
      Map<UUID, WholePieceSearch.Lot> pools = fitsFree ? freePools : pools(Tier.HELD);
      List<WholePieceSearch.LotTake> takes = new ArrayList<>();
      for (ProposalLot lot : lots) {
        List<UUID> picked = byLot.get(lot.batchId());
        if (picked == null) {
          continue;
        }
        WholePieceSearch.Lot pool = pools.get(lot.batchId());
        int left =
            (int) pool.pieces().stream().filter(piece -> !picked.contains(piece.id())).count();
        takes.add(
            new WholePieceSearch.LotTake(
                lot.batchId(), picked.stream().sorted().toList(), units(picked), left));
      }
      Set<UUID> usedLots = byLot.keySet();
      Compatibility compatibility;
      if (usedLots.size() == 1) {
        compatibility = Compatibility.SINGLE_LOT;
      } else if (input.confirmedGroups().stream().anyMatch(group -> group.containsAll(usedLots))) {
        compatibility = Compatibility.CONFIRMED;
      } else if (input.toneAcceptedGroups().stream()
          .anyMatch(group -> group.containsAll(usedLots))) {
        compatibility = Compatibility.TONE_ACCEPTED;
      } else {
        compatibility = Compatibility.PENDING_CONFIRMATION;
      }
      BlockedReason block = null;
      if (input.singleLotRequired() && usedLots.size() > 1) {
        block = BlockedReason.SINGLE_LOT_REQUIRED;
      } else if (compatibility == Compatibility.PENDING_CONFIRMATION) {
        block = BlockedReason.COMPATIBILITY_CONFIRMATION_REQUIRED;
      }
      QuantityOption option =
          option("M:" + pieceKey(takes), takes, compatibility, block).as(OptionPlacement.MANUAL);
      return rank(option, block == null, takes);
    }

    private long units(List<UUID> pieceIds) {
      return pieceIds.stream()
          .mapToLong(id -> toUnits(piecesById.get(id).canonicalMeasure()))
          .sum();
    }

    // ---------------------------------------------------------------- list

    private Ranked withOption(Ranked ranked, QuantityOption option) {
      return new Ranked(
          option,
          ranked.definite(),
          ranked.tier(),
          ranked.remnantPenalty(),
          ranked.closedLots(),
          ranked.dates(),
          ranked.units(),
          ranked.pieceKey());
    }

    /** Marks, per side, the option nearest the request; manual picks are not computed options. */
    /** Each option's facts in criterion order; never an age claim without known dates. */
    private List<QuantityOption> withReasons(List<QuantityOption> options) {
      boolean anyRemnant = options.stream().anyMatch(QuantityOption::leavesRemnant);
      Instant oldest =
          aged
              ? lots.stream()
                  .filter(QuantityEvaluator::hasEligible)
                  .map(ProposalLot::productionDate)
                  .min(Comparator.naturalOrder())
                  .orElse(null)
              : null;
      List<QuantityOption> result = new ArrayList<>();
      for (QuantityOption option : options) {
        List<ProposalReason> reasons = new ArrayList<>();
        if (option.source() == StockSource.HELD && option.placement() != OptionPlacement.MANUAL) {
          // Without a complete search the absence of a free option is only what was found.
          reasons.add(
              ProposalReason.of(
                  limitExceeded
                      ? ProposalReason.Code.FREE_STOCK_NOT_FOUND
                      : ProposalReason.Code.FREE_STOCK_INSUFFICIENT));
        }
        if (option.leavesRemnant()) {
          // Only a manual pick: how many pieces stay behind and how much they hold.
          List<QuantityOption.LotPart> remnantLots =
              option.lots().stream().filter(QuantityOption.LotPart::remnant).toList();
          BigDecimal remnant =
              remnantLots.stream()
                  .map(QuantityOption.LotPart::remnantQuantity)
                  .filter(Objects::nonNull)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          int pieces = remnantLots.stream().mapToInt(part -> part.remnantPieceIds().size()).sum();
          reasons.add(
              new ProposalReason(ProposalReason.Code.LEAVES_REMNANT, remnant, pieces, null));
        } else if (anyRemnant) {
          reasons.add(ProposalReason.of(ProposalReason.Code.NO_REMNANT));
        }
        int closed = (int) option.lots().stream().filter(QuantityOption.LotPart::closesLot).count();
        if (closed > 0) {
          reasons.add(new ProposalReason(ProposalReason.Code.CLOSES_LOTS, null, closed, null));
        }
        if (oldest != null
            && option.lots().stream().anyMatch(part -> oldest.equals(part.productionDate()))) {
          reasons.add(new ProposalReason(ProposalReason.Code.OLDER_STOCK, null, null, oldest));
        }
        result.add(option.withReasons(reasons));
      }
      return result;
    }
  }

  /** Older first: production dates compared in order, an unknown date as the youngest. */
  static int compareAges(List<Instant> left, List<Instant> right) {
    Comparator<Instant> date = Comparator.nullsLast(Comparator.naturalOrder());
    for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
      int compared = date.compare(left.get(i), right.get(i));
      if (compared != 0) {
        return compared;
      }
    }
    return 0;
  }

  private static String pieceKey(List<WholePieceSearch.LotTake> takes) {
    String pieces =
        takes.stream()
            .flatMap(take -> take.pieceIds().stream())
            .map(UUID::toString)
            .sorted()
            .collect(Collectors.joining(","));
    return sha256(pieces).substring(0, 16);
  }

  private static QuantityEvaluationResult result(
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

  /**
   * Fingerprint of every fact the options depend on: pieces, lots, compatibility evidence and what
   * quotes hold. A change makes an earlier proposal stale.
   */
  static String fingerprint(Input input, List<ProposalLot> lots) {
    StringBuilder text = new StringBuilder();
    text.append(input.canonicalTarget().stripTrailingZeros().toPlainString()).append('|');
    text.append(input.singleLotRequired()).append('|');
    lots.forEach(
        lot -> {
          text.append(lot.batchId())
              .append('#')
              .append(lot.widthEvidence())
              .append('#')
              .append(lot.productionDate())
              .append(';');
          lot.pieces().stream()
              .sorted(Comparator.comparing(ProposalPiece::stockUnitId))
              .forEach(
                  piece ->
                      text.append(piece.stockUnitId())
                          .append(':')
                          .append(piece.version())
                          .append(':')
                          .append(piece.state())
                          .append(':')
                          .append(
                              piece.canonicalMeasure() == null
                                  ? "-"
                                  : piece.canonicalMeasure().stripTrailingZeros().toPlainString())
                          .append(';'));
        });
    input.confirmedGroups().stream()
        .map(QuantityEvaluator::sortedKey)
        .sorted()
        .forEach(key -> text.append("C").append(key));
    input.toneAcceptedGroups().stream()
        .map(QuantityEvaluator::sortedKey)
        .sorted()
        .forEach(key -> text.append("T").append(key));
    input.holds().pieceHolds().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                text.append("H")
                    .append(entry.getKey())
                    .append(':')
                    .append(entry.getValue().quoteLineId())
                    .append(':')
                    .append(entry.getValue().verified()));
    input.holds().lotIntents().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                text.append("I")
                    .append(entry.getKey())
                    .append(':')
                    .append(
                        entry.getValue().canonicalTotal() == null
                            ? "?"
                            : entry
                                .getValue()
                                .canonicalTotal()
                                .stripTrailingZeros()
                                .toPlainString())
                    .append(':')
                    .append(entry.getValue().intents().size()));
    return sha256(text.toString());
  }

  private static String sortedKey(Set<UUID> group) {
    return group.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
  }

  static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
