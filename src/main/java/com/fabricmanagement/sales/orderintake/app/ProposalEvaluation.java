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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One evaluation (STOCK-PROPOSAL-2): searches the lots the line may use, picks the cards and their
 * order, adds the salesperson's changed pieces and states the result. The rules are in {@link
 * QuantityEvaluator}'s description; the ranking in {@link ProposalRanking}.
 *
 * <p>The cards (Fatih, 2026-10-15), each healthy stock ranked first among the selections it allows:
 *
 * <ol>
 *   <li>meets the request within the tolerance (the request up to the upper limit): the opening;
 *   <li>the most within the upper limit, when it is more than the first: selling more;
 *   <li>short of the request within the lower limit;
 *   <li>only when none of these exists: the nearest above the upper limit, then the nearest below
 *       the lower limit;
 *   <li>stock others hold, only when free stock cannot reach the lower limit.
 * </ol>
 *
 * <p>The salesperson's changed pieces come first. The first card is the opening.
 */
final class ProposalEvaluation {

  /**
   * The lots one search may combine: each lot alone when the customer requires a single dye lot,
   * otherwise every lot together (Fatih, 2026-10-15: several dye lots allowed means the customer
   * accepts them).
   */
  private record Run(List<UUID> lots) {}

  /** The cards one tier of stock offers, in their order, the opening first. */
  private record Cards(List<Ranked> cards, boolean reachesLowerLimit) {
    static final Cards NONE = new Cards(List.of(), false);

    Optional<Ranked> opening() {
      return cards.stream().findFirst();
    }

    /** Only the cards that take stock others hold. */
    Cards onlyHeld() {
      return new Cards(
          cards.stream().filter(ranked -> ranked.option().source() == StockSource.HELD).toList(),
          reachesLowerLimit);
    }
  }

  private final EvaluationContext context;
  private final Counts counts;
  private final String fingerprint;
  private final long target;
  private final long windowLow;
  private final long windowHigh;
  private boolean limitExceeded;

  /** The tenant-first ranking: healthy stock first, then closeness. */
  private final Comparator<Ranked> tenantFirst;

  ProposalEvaluation(EvaluationContext context, Counts counts, String fingerprint) {
    this.context = context;
    this.counts = counts;
    this.fingerprint = fingerprint;
    this.target = context.target();
    this.windowLow = context.windowLow();
    this.windowHigh = context.windowHigh();
    this.tenantFirst = ProposalRanking.tenantFirst(target);
  }

  Evaluation run() {
    List<Run> runs = runs();
    Cards free = cards(Tier.FREE, runs);
    // Held stock only when free stock cannot reach the lower tolerance limit (Fatih,
    // 2026-10-13); then it is an option to weigh, never to apply.
    Cards held = free.reachesLowerLimit() ? Cards.NONE : cards(Tier.HELD, runs).onlyHeld();
    Optional<Ranked> opening = free.opening().or(held::opening);

    List<Ranked> chosen = new ArrayList<>();
    List<UUID> unavailable = List.of();
    if (!context.input().manualPieceIds().isEmpty()) {
      unavailable = unavailableManualPieces();
      if (unavailable.isEmpty()) {
        chosen.add(manual());
      }
    }
    chosen.addAll(free.cards());
    chosen.addAll(held.cards());

    // The same pieces are listed once; the earlier card wins.
    Map<String, Ranked> unique = new LinkedHashMap<>();
    for (Ranked ranked : chosen) {
      unique.putIfAbsent(ranked.pieceKey(), ranked);
    }
    String openingKey = opening.map(Ranked::pieceKey).orElse(null);
    List<QuantityOption> listed = new ArrayList<>();
    for (Ranked ranked : unique.values()) {
      QuantityOption option = ranked.option();
      if (option.placement() != OptionPlacement.MANUAL) {
        option =
            option.as(
                ranked.pieceKey().equals(openingKey)
                    ? OptionPlacement.MAIN
                    : ranked.units() < target ? OptionPlacement.BELOW : OptionPlacement.ABOVE);
      }
      listed.add(option);
    }

    List<QuantityOption> options = ProposalReasons.withReasons(context, limitExceeded, listed);
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

  // ---------------------------------------------------------------- cards

  private List<Run> runs() {
    List<UUID> ordered = context.lots().stream().map(ProposalLot::batchId).toList();
    if (!context.input().singleLotRequired()) {
      return List.of(new Run(ordered));
    }
    return ordered.stream().map(lot -> new Run(List.of(lot))).toList();
  }

  /**
   * The cards one tier of stock offers (Fatih, 2026-10-15): meets the request, the most within the
   * upper limit, short within the lower limit; only when none exists, the nearest above and below
   * outside the tolerance. Each search ranks healthy stock first; no computed selection leaves a
   * lot fewer normal pieces than its lot-health minimum.
   */
  private Cards cards(Tier tier, List<Run> runs) {
    Map<UUID, WholePieceSearch.Lot> pools = StockPools.pools(context, tier);
    List<List<ProposalSearch.SearchLot>> searchable = new ArrayList<>();
    for (Run run : runs) {
      List<ProposalSearch.SearchLot> lots = searchLots(run, pools);
      if (!lots.isEmpty()) {
        searchable.add(lots);
      }
    }
    Comparator<Ranked> mostFirst =
        Comparator.comparingLong(Ranked::units).reversed().thenComparing(tenantFirst);
    Comparator<Ranked> nearestAbove =
        Comparator.comparingLong(Ranked::units).thenComparing(tenantFirst);

    Optional<Ranked> meets =
        best(
            searchable,
            pools,
            lots -> ProposalSearch.bestWithin(lots, target, windowHigh, target),
            tenantFirst);
    Optional<Ranked> most =
        meets.flatMap(
            first ->
                best(
                        searchable,
                        pools,
                        lots -> ProposalSearch.largestUpTo(lots, windowHigh),
                        mostFirst)
                    .filter(candidate -> candidate.units() > first.units()));
    Optional<Ranked> shortWithin =
        windowLow < target
            ? best(
                searchable,
                pools,
                lots -> ProposalSearch.bestWithin(lots, windowLow, target - 1, target),
                tenantFirst)
            : Optional.empty();

    List<Ranked> cards = new ArrayList<>();
    meets.ifPresent(cards::add);
    most.ifPresent(cards::add);
    shortWithin.ifPresent(cards::add);
    if (!cards.isEmpty()) {
      return new Cards(cards, true);
    }
    Optional<Ranked> above =
        best(
            searchable,
            pools,
            lots -> ProposalSearch.smallestFrom(lots, windowHigh + 1),
            nearestAbove);
    Optional<Ranked> below =
        windowLow > 1
            ? best(
                searchable,
                pools,
                lots -> ProposalSearch.largestUpTo(lots, windowLow - 1),
                mostFirst)
            : Optional.empty();
    above.ifPresent(cards::add);
    below.ifPresent(cards::add);
    return new Cards(cards, above.isPresent());
  }

  private List<ProposalSearch.SearchLot> searchLots(
      Run run, Map<UUID, WholePieceSearch.Lot> pools) {
    int minKeep = context.lotHealth().minHealthyPieces();
    return run.lots().stream()
        .map(pools::get)
        .filter(Objects::nonNull)
        .filter(lot -> !lot.pieces().isEmpty())
        .map(lot -> new ProposalSearch.SearchLot(lot, context.ageRanks().get(lot.id()), minKeep))
        .toList();
  }

  /** The best of one search over every run, by {@code order}. */
  private Optional<Ranked> best(
      List<List<ProposalSearch.SearchLot>> searchable,
      Map<UUID, WholePieceSearch.Lot> pools,
      Function<List<ProposalSearch.SearchLot>, ProposalSearch.Result> search,
      Comparator<Ranked> order) {
    List<Ranked> found = new ArrayList<>();
    for (List<ProposalSearch.SearchLot> lots : searchable) {
      ProposalSearch.Result result = search.apply(lots);
      limitExceeded |= result.limitExceeded();
      result.best().ifPresent(selection -> found.add(ranked(pools, selection)));
    }
    return found.stream().min(order);
  }

  private Ranked ranked(
      Map<UUID, WholePieceSearch.Lot> pools, WholePieceSearch.Selection selection) {
    QuantityOption option =
        OptionBuilder.option(
            context,
            pools,
            "S:" + ProposalRanking.pieceKey(selection.takes()),
            selection.takes(),
            compatibilityOf(selection.lotCount()),
            null);
    return ProposalRanking.rank(context, pools, option, true, selection.takes());
  }

  /** One lot, or several the line accepts (Fatih, 2026-10-15). */
  private static Compatibility compatibilityOf(int lots) {
    return lots <= 1 ? Compatibility.SINGLE_LOT : Compatibility.MULTIPLE_LOTS_ACCEPTED;
  }

  // ---------------------------------------------------------------- changed pieces

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
   * The salesperson's changed pieces with the same checks: the single-lot requirement, holds and
   * the remnant rule over the pool they were taken from (free when they fit free stock). Short
   * pieces left behind do not count as kept.
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
    boolean several = byLot.size() > 1;
    BlockedReason block =
        context.input().singleLotRequired() && several ? BlockedReason.SINGLE_LOT_REQUIRED : null;
    QuantityOption option =
        OptionBuilder.option(
                context,
                pools,
                "M:" + ProposalRanking.pieceKey(takes),
                takes,
                compatibilityOf(byLot.size()),
                block)
            .as(OptionPlacement.MANUAL);
    return ProposalRanking.rank(context, pools, option, block == null, takes);
  }
}
