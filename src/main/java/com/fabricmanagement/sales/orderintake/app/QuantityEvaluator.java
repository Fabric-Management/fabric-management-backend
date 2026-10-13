package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalReason;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult.EvaluationStatus;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.BlockedReason;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.Compatibility;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.HeldStock;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.HeldStockKind;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionKind;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionRole;
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
import java.util.LinkedHashSet;
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
 * <p>Compatibility and the single-lot requirement are prerequisites. When free stock can meet the
 * request within the agreed tolerance, the main proposal is one of those free options (Fatih,
 * 2026-10-12); otherwise it is the best construction overall. Among the candidates the tenant comes
 * first: free stock, then no single piece left, then lots that fit the remaining target taken
 * whole, then older stock, fewer lots and, last, closeness to the request. Stock other quotes hold
 * is evaluated only when free stock has no option within the agreed tolerance. The nearest options
 * below and at-or-above the request are kept beside the main one, and options outside the tolerance
 * are shown, never withheld. Options are returned in ascending quantity with the main option's
 * index. A tolerance side that was not agreed allows no deviation on that side.
 */
public final class QuantityEvaluator {

  /** Search resolution: 0.001 of the canonical unit (millimetre or gram). */
  static final BigDecimal UNITS_PER_CANONICAL = new BigDecimal("1000");

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private QuantityEvaluator() {}

  /**
   * Everything the evaluation needs, already in the canonical unit. Tolerances are percentages; a
   * missing side was not agreed and allows no deviation. {@code manualPieceIds} is the
   * salesperson's own pick, evaluated as one more option with the same checks.
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

    boolean any() {
      return !pieceHolds.isEmpty()
          || lotIntents.values().stream().anyMatch(lot -> !lot.intents().isEmpty());
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
      String pieceKey,
      boolean mainCandidate) {}

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

    /**
     * The tenant-first ranking: prerequisites met, free stock, no single piece left, more lots
     * taken whole, older stock, fewer lots, closer to the request, covering the request, then the
     * pieces themselves.
     */
    private final Comparator<Ranked> tenantFirst;

    /** Ascending quantity; same quantity by the tenant-first ranking. */
    private final Comparator<Ranked> listedOrder;

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
      this.lotsById = new LinkedHashMap<>();
      for (ProposalLot lot : lots) {
        lotsById.put(lot.batchId(), lot);
        for (ProposalPiece piece : lot.pieces()) {
          piecesById.put(piece.stockUnitId(), piece);
          lotOfPiece.put(piece.stockUnitId(), lot.batchId());
        }
        free.put(lot.batchId(), lotFree(lot));
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
      List<Ranked> freeCandidates = candidates(Tier.FREE, runs);
      List<Ranked> freeWithin = withinTolerance(freeCandidates);
      // Free stock is sufficient when it has an option within the tolerance (Fatih, 2026-10-12).
      List<Ranked> heldCandidates =
          !freeWithin.isEmpty()
              ? List.of()
              : candidates(Tier.HELD, runs).stream()
                  .filter(ranked -> ranked.option().source() == StockSource.HELD)
                  .toList();

      List<Ranked> chosen = new ArrayList<>();
      Optional<Ranked> main =
          !freeWithin.isEmpty()
              ? freeWithin.stream().min(tenantFirst)
              : concat(freeCandidates, heldCandidates).stream()
                  .filter(Ranked::mainCandidate)
                  .filter(Ranked::definite)
                  .min(tenantFirst);
      main.ifPresent(chosen::add);
      // The tenant-first construction stays visible when the tolerance picked another main.
      freeCandidates.stream()
          .filter(Ranked::mainCandidate)
          .filter(Ranked::definite)
          .min(tenantFirst)
          .ifPresent(chosen::add);
      chosen.addAll(nearest(freeCandidates));
      chosen.addAll(nearest(heldCandidates));
      // When held stock is evaluated, its own best candidate is an alternative too.
      best(heldCandidates).ifPresent(chosen::add);

      // The same pieces are listed once; the main role wins.
      Map<String, Ranked> unique = new LinkedHashMap<>();
      for (Ranked ranked : chosen) {
        unique.putIfAbsent(ranked.pieceKey(), ranked);
      }
      List<Ranked> listed = new ArrayList<>();
      String mainKey = main.map(Ranked::pieceKey).orElse(null);
      for (Ranked ranked : unique.values()) {
        OptionRole role =
            ranked.pieceKey().equals(mainKey)
                ? OptionRole.MAIN
                : ranked.units() < target ? OptionRole.BELOW : OptionRole.ABOVE;
        listed.add(withOption(ranked, ranked.option().as(role, false)));
      }

      List<UUID> unavailable = List.of();
      if (!input.manualPieceIds().isEmpty()) {
        unavailable = unavailableManualPieces();
        if (unavailable.isEmpty()) {
          listed.add(manual());
        }
      }

      listed.sort(listedOrder);
      List<QuantityOption> options = withNearest(listed);
      options = withReasons(options);
      Integer mainIndex = null;
      for (int i = 0; i < options.size(); i++) {
        if (options.get(i).role() == OptionRole.MAIN) {
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
     * The tier's main constructions (definite runs) and nearest options (all runs). A pending
     * combination is kept only when it uses several lots no definite group covers and is nearer to
     * the request than the definite options on its side (SOI S05).
     */
    private List<Ranked> candidates(Tier tier, List<Run> runs) {
      Map<UUID, WholePieceSearch.Lot> pools = pools(tier);
      List<Ranked> definite = new ArrayList<>();
      List<Ranked> pending = new ArrayList<>();
      for (Run run : runs) {
        List<WholePieceSearch.Lot> runLots =
            run.lots().stream().map(pools::get).filter(Objects::nonNull).toList();
        if (runLots.stream().allMatch(lot -> lot.pieces().isEmpty())) {
          continue;
        }
        if (run.definite()) {
          construct(runLots).ifPresent(selection -> definite.add(ranked(run, selection, true)));
        }
        WholePieceSearch.Result nearest = WholePieceSearch.search(runLots, target, true);
        limitExceeded |= nearest.limitExceeded();
        List<WholePieceSearch.Selection> found = new ArrayList<>();
        nearest.exact().ifPresent(found::add);
        nearest.above().ifPresent(found::add);
        nearest.below().ifPresent(found::add);
        for (WholePieceSearch.Selection selection : found) {
          Ranked ranked = ranked(run, selection, false);
          if (run.definite()) {
            definite.add(ranked);
          } else if (selection.lotCount() >= 2 && !coveredByDefiniteGroup(selection, runs)) {
            pending.add(ranked);
          }
        }
      }
      List<Ranked> all = new ArrayList<>(definite);
      for (Ranked candidate : pending) {
        boolean below = candidate.units() < target;
        Optional<Ranked> definiteBest = nearestOnSide(definite, below);
        long distance = Math.abs(candidate.units() - target);
        if (definiteBest.isEmpty() || distance < Math.abs(definiteBest.get().units() - target)) {
          all.add(candidate);
        }
      }
      return all;
    }

    /** Definite candidates that meet the request within the agreed tolerance. */
    private static List<Ranked> withinTolerance(List<Ranked> candidates) {
      return candidates.stream()
          .filter(Ranked::definite)
          .filter(ranked -> ranked.option().toleranceStatus() == ToleranceStatus.WITHIN)
          .toList();
    }

    /** The best within the tolerance, otherwise the best tenant-first construction. */
    private Optional<Ranked> best(List<Ranked> candidates) {
      List<Ranked> within = withinTolerance(candidates);
      if (!within.isEmpty()) {
        return within.stream().min(tenantFirst);
      }
      return candidates.stream()
          .filter(Ranked::mainCandidate)
          .filter(Ranked::definite)
          .min(tenantFirst);
    }

    /** The nearest definite option on each side and nearer pending ones. */
    private List<Ranked> nearest(List<Ranked> candidates) {
      List<Ranked> result = new ArrayList<>();
      for (boolean below : new boolean[] {true, false}) {
        nearestOnSide(candidates.stream().filter(Ranked::definite).toList(), below)
            .ifPresent(result::add);
        nearestOnSide(candidates.stream().filter(ranked -> !ranked.definite()).toList(), below)
            .ifPresent(result::add);
      }
      return result;
    }

    private Optional<Ranked> nearestOnSide(List<Ranked> candidates, boolean below) {
      Comparator<Ranked> closeness =
          below
              ? Comparator.comparingLong(Ranked::units).reversed()
              : Comparator.comparingLong(Ranked::units);
      return candidates.stream()
          .filter(ranked -> below ? ranked.units() < target : ranked.units() >= target)
          .min(closeness.thenComparing(tenantFirst));
    }

    private boolean coveredByDefiniteGroup(WholePieceSearch.Selection selection, List<Run> runs) {
      Set<UUID> used =
          selection.takes().stream()
              .map(WholePieceSearch.LotTake::lotId)
              .collect(Collectors.toSet());
      return runs.stream().filter(Run::definite).anyMatch(run -> run.lots().containsAll(used));
    }

    /**
     * The tenant-first construction for one compatible set of lots. Lots that fit the remaining
     * target are taken whole, oldest first and larger first, with the target updated after each.
     * One lot then completes the rest: the first lot (same order) with a combination that leaves no
     * single piece, choosing its combination nearest the remaining target; only when no lot has one
     * may a single piece stay.
     */
    private Optional<WholePieceSearch.Selection> construct(List<WholePieceSearch.Lot> runLots) {
      List<WholePieceSearch.Lot> order =
          runLots.stream()
              .filter(lot -> !lot.pieces().isEmpty())
              .sorted(
                  Comparator.comparing(
                          (WholePieceSearch.Lot lot) -> lotsById.get(lot.id()).productionDate(),
                          Comparator.nullsLast(Comparator.naturalOrder()))
                      .thenComparing(
                          Comparator.comparingLong(
                                  (WholePieceSearch.Lot lot) ->
                                      Math.min(lot.total(), lot.capacity()))
                              .reversed())
                      .thenComparing(WholePieceSearch.Lot::id))
              .toList();
      List<WholePieceSearch.LotTake> takes = new ArrayList<>();
      Set<UUID> used = new LinkedHashSet<>();
      long remaining = target;
      for (WholePieceSearch.Lot lot : order) {
        long total = lot.total();
        if (total <= remaining && total <= lot.capacity()) {
          List<UUID> all = lot.pieces().stream().map(WholePieceSearch.Piece::id).toList();
          takes.add(new WholePieceSearch.LotTake(lot.id(), all, total, 0));
          used.add(lot.id());
          remaining -= total;
        }
      }
      if (remaining > 0) {
        Optional<WholePieceSearch.LotTake> completion = Optional.empty();
        for (boolean allowRemnant : new boolean[] {false, true}) {
          for (WholePieceSearch.Lot lot : order) {
            if (used.contains(lot.id())) {
              continue;
            }
            WholePieceSearch.Result within =
                WholePieceSearch.search(List.of(lot), remaining, allowRemnant);
            limitExceeded |= within.limitExceeded();
            Optional<WholePieceSearch.Selection> best = closest(within, remaining);
            if (best.isPresent()) {
              completion = Optional.of(best.get().takes().getFirst());
              break;
            }
          }
          if (completion.isPresent()) {
            break;
          }
        }
        completion.ifPresent(takes::add);
      }
      if (takes.isEmpty()) {
        return Optional.empty();
      }
      long total = takes.stream().mapToLong(WholePieceSearch.LotTake::units).sum();
      return Optional.of(new WholePieceSearch.Selection(total, takes));
    }

    /** Exact first; otherwise the nearer of above and below, above (covering) on a tie. */
    private static Optional<WholePieceSearch.Selection> closest(
        WholePieceSearch.Result result, long remaining) {
      if (result.exact().isPresent()) {
        return result.exact();
      }
      if (result.above().isEmpty()) {
        return result.below();
      }
      if (result.below().isEmpty()) {
        return result.above();
      }
      long up = result.above().get().total() - remaining;
      long down = remaining - result.below().get().total();
      return down < up ? result.below() : result.above();
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

    private Ranked ranked(Run run, WholePieceSearch.Selection selection, boolean mainCandidate) {
      QuantityOption option =
          option(
              "S:" + pieceKey(selection.takes()),
              selection.takes(),
              run.compatibility(),
              run.definite() ? null : BlockedReason.COMPATIBILITY_CONFIRMATION_REQUIRED);
      return rank(option, run.definite(), selection.takes(), mainCandidate);
    }

    private Ranked rank(
        QuantityOption option,
        boolean definite,
        List<WholePieceSearch.LotTake> takes,
        boolean mainCandidate) {
      int penalty = option.leavesSingleRemnant() ? 2 : option.remnantUnknown() ? 1 : 0;
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
          pieceKey(takes),
          mainCandidate);
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
      boolean remnant = parts.stream().anyMatch(QuantityOption.LotPart::singleRemnant);
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
          OptionRole.ABOVE,
          false,
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
      boolean single = take.remainingPieces() == 1;
      ProposalPiece remnantPiece = null;
      if (single) {
        // The one pool piece left: free pieces first, as the free pool was searched.
        remnantPiece =
            remainingEligible.stream()
                .filter(piece -> !input.holds().pieceHolds().containsKey(piece.stockUnitId()))
                .findFirst()
                .orElse(remainingEligible.isEmpty() ? null : remainingEligible.getFirst());
      }
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
          single,
          remnantPiece == null ? null : remnantPiece.stockUnitId(),
          remnantPiece == null ? null : input.toLineUnit().apply(remnantPiece.canonicalMeasure()),
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

    /** Against the agreed tolerance; a side that was not agreed allows no deviation. */
    private ToleranceStatus tolerance(BigDecimal difference) {
      if (difference.signum() == 0) {
        return ToleranceStatus.WITHIN;
      }
      BigDecimal agreed =
          difference.signum() > 0 ? input.agreedToleranceUpPct() : input.agreedToleranceDownPct();
      BigDecimal limit = agreed == null ? BigDecimal.ZERO : agreed;
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
          option("M:" + pieceKey(takes), takes, compatibility, block).as(OptionRole.MANUAL, false);
      return rank(option, block == null, takes, false);
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
          ranked.pieceKey(),
          ranked.mainCandidate());
    }

    /** Marks, per side, the option nearest the request; manual picks are not computed options. */
    private List<QuantityOption> withNearest(List<Ranked> listed) {
      Ranked nearestBelow = null;
      Ranked nearestAbove = null;
      for (Ranked ranked : listed) {
        if (ranked.option().role() == OptionRole.MANUAL) {
          continue;
        }
        if (ranked.units() < target) {
          if (nearestBelow == null || ranked.units() > nearestBelow.units()) {
            nearestBelow = ranked;
          }
        } else if (nearestAbove == null || ranked.units() < nearestAbove.units()) {
          nearestAbove = ranked;
        }
      }
      List<QuantityOption> options = new ArrayList<>();
      for (Ranked ranked : listed) {
        boolean nearest = ranked == nearestBelow || ranked == nearestAbove;
        options.add(ranked.option().as(ranked.option().role(), nearest));
      }
      return options;
    }

    /** Each option's facts in criterion order; never an age claim without known dates. */
    private List<QuantityOption> withReasons(List<QuantityOption> options) {
      boolean anyHolds = input.holds().any();
      boolean anyRemnant = options.stream().anyMatch(QuantityOption::leavesSingleRemnant);
      Instant oldest =
          aged
              ? lots.stream()
                  .filter(QuantityEvaluator::hasEligible)
                  .map(ProposalLot::productionDate)
                  .min(Comparator.naturalOrder())
                  .orElse(null)
              : null;
      int fewest = options.stream().mapToInt(QuantityOption::lotCount).min().orElse(0);
      int most = options.stream().mapToInt(QuantityOption::lotCount).max().orElse(0);
      BigDecimal closest =
          options.stream()
              .map(option -> option.difference().abs())
              .min(Comparator.naturalOrder())
              .orElse(null);
      List<QuantityOption> result = new ArrayList<>();
      for (QuantityOption option : options) {
        List<ProposalReason> reasons = new ArrayList<>();
        if (option.source() == StockSource.HELD && option.role() != OptionRole.MANUAL) {
          reasons.add(ProposalReason.of(ProposalReason.Code.FREE_STOCK_INSUFFICIENT));
        } else if (option.source() == StockSource.FREE && anyHolds) {
          reasons.add(ProposalReason.of(ProposalReason.Code.FREE_STOCK_ONLY));
        }
        if (option.leavesSingleRemnant()) {
          BigDecimal remnant =
              option.lots().stream()
                  .filter(QuantityOption.LotPart::singleRemnant)
                  .map(QuantityOption.LotPart::remnantQuantity)
                  .filter(Objects::nonNull)
                  .findFirst()
                  .orElse(null);
          int lotsWithRemnant =
              (int) option.lots().stream().filter(QuantityOption.LotPart::singleRemnant).count();
          reasons.add(
              new ProposalReason(
                  ProposalReason.Code.LEAVES_SINGLE_PIECE, remnant, lotsWithRemnant, null));
        } else if (option.remnantUnknown()) {
          reasons.add(ProposalReason.of(ProposalReason.Code.SINGLE_PIECE_MAY_REMAIN));
        } else if (anyRemnant) {
          reasons.add(ProposalReason.of(ProposalReason.Code.NO_SINGLE_PIECE_LEFT));
        }
        int closed = (int) option.lots().stream().filter(QuantityOption.LotPart::closesLot).count();
        if (closed > 0) {
          reasons.add(new ProposalReason(ProposalReason.Code.CLOSES_LOTS, null, closed, null));
        }
        if (oldest != null
            && option.lots().stream().anyMatch(part -> oldest.equals(part.productionDate()))) {
          reasons.add(new ProposalReason(ProposalReason.Code.OLDER_STOCK, null, null, oldest));
        }
        if (most > fewest && option.lotCount() == fewest) {
          reasons.add(
              new ProposalReason(ProposalReason.Code.FEWEST_LOTS, null, option.lotCount(), null));
        }
        if (option.difference().signum() == 0) {
          reasons.add(ProposalReason.of(ProposalReason.Code.EXACT_REQUEST));
        } else if (options.size() > 1 && option.difference().abs().compareTo(closest) == 0) {
          reasons.add(
              new ProposalReason(
                  ProposalReason.Code.CLOSEST_TO_REQUEST, option.difference().abs(), null, null));
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

  private static <T> List<T> concat(List<T> left, List<T> right) {
    List<T> all = new ArrayList<>(left);
    all.addAll(right);
    return all;
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
