package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.sales.orderintake.app.ProposalRanking.Ranked;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.Counts;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluator.Evaluation;
import com.fabricmanagement.sales.orderintake.app.StockPools.Tier;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalSearch;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult.EvaluationStatus;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.BlockedReason;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.Compatibility;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionPlacement;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.StockSource;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One evaluation (STOCK-PROPOSAL-2): searches each compatible set of lots, picks where the proposal
 * opens and the cards beside it, adds the salesperson's own pick and orders the list. The rules are
 * in {@link QuantityEvaluator}'s description; the ranking in {@link ProposalRanking}.
 */
final class ProposalEvaluation {

  private record Run(List<UUID> lots, Compatibility compatibility, int order) {
    boolean definite() {
      return compatibility != Compatibility.PENDING_CONFIRMATION;
    }
  }

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

  private final EvaluationContext context;
  private final Counts counts;
  private final String fingerprint;
  private final long target;
  private final long windowLow;
  private final long windowHigh;
  private boolean limitExceeded;

  /** The tenant-first ranking. */
  private final Comparator<Ranked> tenantFirst;

  /** Ascending quantity; same quantity by the tenant-first ranking. */
  private final Comparator<Ranked> listedOrder;

  /** The nearest at or above the request first, then the ranking. */
  private final Comparator<Ranked> aboveOrder;

  /** The nearest below the request first, then the ranking. */
  private final Comparator<Ranked> belowOrder;

  ProposalEvaluation(EvaluationContext context, Counts counts, String fingerprint) {
    this.context = context;
    this.counts = counts;
    this.fingerprint = fingerprint;
    this.target = context.target();
    this.windowLow = context.windowLow();
    this.windowHigh = context.windowHigh();
    this.tenantFirst = ProposalRanking.tenantFirst(target);
    this.listedOrder = ProposalRanking.ascending(tenantFirst);
    this.aboveOrder = ProposalRanking.ascending(tenantFirst);
    this.belowOrder = ProposalRanking.descending(tenantFirst);
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
      listed.add(ranked.withOption(ranked.option().as(placement)));
    }

    List<UUID> unavailable = List.of();
    if (!context.input().manualPieceIds().isEmpty()) {
      unavailable = unavailableManualPieces();
      if (unavailable.isEmpty()) {
        listed.add(manual());
      }
    }

    listed.sort(listedOrder);
    List<QuantityOption> options =
        ProposalReasons.withReasons(
            context, limitExceeded, listed.stream().map(Ranked::option).toList());
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
        QuantityEvaluator.result(
            context.input(),
            status,
            options,
            mainIndex,
            !limitExceeded,
            counts,
            List.copyOf(reasons),
            context.aged()),
        fingerprint,
        unavailable);
  }

  // ---------------------------------------------------------------- candidates

  private List<Run> runs() {
    List<Run> runs = new ArrayList<>();
    List<UUID> ordered = context.lots().stream().map(ProposalLot::batchId).toList();
    for (UUID lot : ordered) {
      runs.add(new Run(List.of(lot), Compatibility.SINGLE_LOT, runs.size()));
    }
    if (context.input().singleLotRequired() || ordered.size() < 2) {
      return runs;
    }
    for (Set<UUID> group : context.input().confirmedGroups()) {
      List<UUID> members = ordered.stream().filter(group::contains).toList();
      if (members.size() >= 2) {
        runs.add(new Run(members, Compatibility.CONFIRMED, runs.size()));
      }
    }
    for (Set<UUID> group : context.input().toneAcceptedGroups()) {
      List<UUID> members = ordered.stream().filter(group::contains).toList();
      if (members.size() >= 2) {
        runs.add(new Run(members, Compatibility.TONE_ACCEPTED, runs.size()));
      }
    }
    runs.add(new Run(ordered, Compatibility.PENDING_CONFIRMATION, runs.size()));
    return runs;
  }

  /**
   * What one tier's stock offers, per compatible set of lots. Definite runs (single lot, confirmed
   * group, tone acceptance) give: the best meeting the request within the tolerance, the nearest at
   * or above it, the best below it within the tolerance and the nearest below it. A pending
   * combination is kept only when it uses several lots no definite group covers and is nearer to
   * the request than the definite options on its side (SOI S05). No computed selection leaves a lot
   * with fewer pieces than its lot-health minimum.
   */
  private Found candidates(Tier tier, List<Run> runs) {
    Map<UUID, WholePieceSearch.Lot> pools = StockPools.pools(context, tier);
    int minKeep = context.lotHealth().minHealthyPieces();
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
              .map(
                  lot ->
                      new ProposalSearch.SearchLot(lot, context.ageRanks().get(lot.id()), minKeep))
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
            candidate -> definiteAbove.isEmpty() || candidate.units() < definiteAbove.get().units())
        .ifPresent(pending::add);
    Optional<Ranked> definiteBelow = below.stream().min(belowOrder);
    pendingBelow.stream()
        .min(belowOrder)
        .filter(
            candidate -> definiteBelow.isEmpty() || candidate.units() > definiteBelow.get().units())
        .ifPresent(pending::add);
    return new Found(covering, above, shortWithin, below, pending);
  }

  private Optional<WholePieceSearch.Selection> found(ProposalSearch.Result result) {
    limitExceeded |= result.limitExceeded();
    return result.best();
  }

  private static boolean severalUncoveredLots(
      WholePieceSearch.Selection selection, List<Run> runs) {
    Set<UUID> used =
        selection.takes().stream().map(WholePieceSearch.LotTake::lotId).collect(Collectors.toSet());
    return used.size() >= 2
        && runs.stream().filter(Run::definite).noneMatch(run -> run.lots().containsAll(used));
  }

  private Ranked ranked(Run run, WholePieceSearch.Selection selection) {
    QuantityOption option =
        OptionBuilder.option(
            context,
            "S:" + ProposalRanking.pieceKey(selection.takes()),
            selection.takes(),
            run.compatibility(),
            run.definite() ? null : BlockedReason.COMPATIBILITY_CONFIRMATION_REQUIRED);
    return ProposalRanking.rank(option, run.definite(), selection.takes());
  }

  // ---------------------------------------------------------------- manual pick

  private List<UUID> unavailableManualPieces() {
    return context.input().manualPieceIds().stream()
        .filter(
            id -> {
              ProposalPiece piece = context.piecesById().get(id);
              return piece == null
                  || piece.state() != PieceState.ELIGIBLE
                  || piece.canonicalMeasure() == null
                  || QuantityEvaluator.toUnits(piece.canonicalMeasure()) <= 0;
            })
        .toList();
  }

  /**
   * The salesperson's pick with the same checks: the single-lot requirement, lot compatibility,
   * holds and the remnant rule over the pool it was taken from (free when it fits free stock).
   */
  private Ranked manual() {
    Map<UUID, List<UUID>> byLot = new LinkedHashMap<>();
    for (UUID id : context.input().manualPieceIds()) {
      byLot.computeIfAbsent(context.lotOfPiece().get(id), ignored -> new ArrayList<>()).add(id);
    }
    Map<UUID, WholePieceSearch.Lot> freePools = StockPools.pools(context, Tier.FREE);
    boolean fitsFree =
        byLot.entrySet().stream()
            .allMatch(
                entry -> {
                  WholePieceSearch.Lot pool = freePools.get(entry.getKey());
                  Set<UUID> poolIds =
                      pool.pieces().stream()
                          .map(WholePieceSearch.Piece::id)
                          .collect(Collectors.toSet());
                  long units = context.units(entry.getValue());
                  return poolIds.containsAll(entry.getValue()) && units <= pool.capacity();
                });
    Map<UUID, WholePieceSearch.Lot> pools =
        fitsFree ? freePools : StockPools.pools(context, Tier.HELD);
    List<WholePieceSearch.LotTake> takes = new ArrayList<>();
    for (ProposalLot lot : context.lots()) {
      List<UUID> picked = byLot.get(lot.batchId());
      if (picked == null) {
        continue;
      }
      WholePieceSearch.Lot pool = pools.get(lot.batchId());
      int left = (int) pool.pieces().stream().filter(piece -> !picked.contains(piece.id())).count();
      takes.add(
          new WholePieceSearch.LotTake(
              lot.batchId(), picked.stream().sorted().toList(), context.units(picked), left));
    }
    Set<UUID> usedLots = byLot.keySet();
    Compatibility compatibility;
    if (usedLots.size() == 1) {
      compatibility = Compatibility.SINGLE_LOT;
    } else if (context.input().confirmedGroups().stream()
        .anyMatch(group -> group.containsAll(usedLots))) {
      compatibility = Compatibility.CONFIRMED;
    } else if (context.input().toneAcceptedGroups().stream()
        .anyMatch(group -> group.containsAll(usedLots))) {
      compatibility = Compatibility.TONE_ACCEPTED;
    } else {
      compatibility = Compatibility.PENDING_CONFIRMATION;
    }
    BlockedReason block = null;
    if (context.input().singleLotRequired() && usedLots.size() > 1) {
      block = BlockedReason.SINGLE_LOT_REQUIRED;
    } else if (compatibility == Compatibility.PENDING_CONFIRMATION) {
      block = BlockedReason.COMPATIBILITY_CONFIRMATION_REQUIRED;
    }
    QuantityOption option =
        OptionBuilder.option(
                context, "M:" + ProposalRanking.pieceKey(takes), takes, compatibility, block)
            .as(OptionPlacement.MANUAL);
    return ProposalRanking.rank(option, block == null, takes);
  }
}
