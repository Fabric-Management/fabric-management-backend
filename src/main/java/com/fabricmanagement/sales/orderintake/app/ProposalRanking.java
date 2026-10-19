package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.sales.orderintake.domain.proposal.FabricRollRank;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.StockSource;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The tenant-first ranking of options (STOCK-PROPOSAL-2; Fatih, 2026-10-12 to 2026-10-14):
 * prerequisites met, free stock, no remnant (only a manual pick can leave one), a remnant lot
 * closed, more lots closed, completing from the lot with fewer pieces, older stock measured by the
 * youngest lot used, fewer lots, closer to the request, covering the request, then the pieces
 * themselves. In one place, so the order is read and changed in one place; the search in {@code
 * ProposalSearch} ranks its patterns the same way. Which cards are shown, and in what order, is
 * {@link ProposalEvaluation}'s.
 */
final class ProposalRanking {

  private ProposalRanking() {}

  /**
   * An option with what the ranking compares. {@code partialSizes} are the pool sizes of the lots
   * taken from partly, largest first; {@code youngestFirst} the age ranks of the lots used,
   * youngest first. Both compare in order, lower first, and a list that is a prefix of the other
   * ties: an added lot never counts for or against on its own, so an option is as deep as its
   * deepest partial lot and as old as its youngest lot.
   */
  record Ranked(
      QuantityOption option,
      boolean definite,
      StockPools.Tier tier,
      int remnantPenalty,
      int remnantClosed,
      int closedLots,
      List<Integer> partialSizes,
      List<Integer> youngestFirst,
      long units,
      String pieceKey,
      FabricRollRank fabricRank) {

    Ranked withFabricRank(FabricRollRank rank) {
      return new Ranked(
          option,
          definite,
          tier,
          remnantPenalty,
          remnantClosed,
          closedLots,
          partialSizes,
          youngestFirst,
          units,
          pieceKey,
          rank);
    }

    /** The same ranking facts with another option (a placement or reasons added). */
    Ranked withOption(QuantityOption newOption) {
      return new Ranked(
          newOption,
          definite,
          tier,
          remnantPenalty,
          remnantClosed,
          closedLots,
          partialSizes,
          youngestFirst,
          units,
          pieceKey,
          fabricRank);
    }
  }

  /**
   * What the ranking compares for an option built from lot takes. {@code pools} are the lots the
   * takes were chosen from: a lot is a remnant lot when its pool holds fewer normal pieces than the
   * lot-health minimum.
   */
  static Ranked rank(
      EvaluationContext context,
      Map<UUID, WholePieceSearch.Lot> pools,
      QuantityOption option,
      boolean definite,
      List<WholePieceSearch.LotTake> takes) {
    // Only a manual pick can leave a remnant; computed selections never do.
    int penalty = option.leavesRemnant() ? 1 : 0;
    int minKeep = context.lotHealth().minHealthyPieces();
    int remnantClosed = 0;
    int closed = 0;
    List<Integer> partialSizes = new ArrayList<>();
    List<Integer> youngestFirst = new ArrayList<>();
    for (WholePieceSearch.LotTake take : takes) {
      WholePieceSearch.Lot pool = pools.get(take.lotId());
      youngestFirst.add(context.ageRanks().get(take.lotId()));
      if (take.remainingPieces() == 0) {
        closed++;
        if (pool.normalPieces() < minKeep) {
          remnantClosed++;
        }
      } else {
        partialSizes.add(pool.pieces().size());
      }
    }
    partialSizes.sort(Comparator.reverseOrder());
    youngestFirst.sort(Comparator.reverseOrder());
    return new Ranked(
        option,
        definite,
        option.source() == StockSource.FREE ? StockPools.Tier.FREE : StockPools.Tier.HELD,
        penalty,
        remnantClosed,
        closed,
        List.copyOf(partialSizes),
        List.copyOf(youngestFirst),
        takes.stream().mapToLong(WholePieceSearch.LotTake::units).sum(),
        pieceKey(takes),
        null);
  }

  /** The tenant-first ranking around {@code target} search units. */
  static Comparator<Ranked> tenantFirst(long target) {
    return Comparator.comparing((Ranked ranked) -> !ranked.definite())
        .thenComparing(Ranked::tier)
        .thenComparingInt(Ranked::remnantPenalty)
        .thenComparing(Comparator.comparingInt(Ranked::remnantClosed).reversed())
        .thenComparing(Comparator.comparingInt(Ranked::closedLots).reversed())
        .thenComparing(Ranked::partialSizes, ProposalRanking::comparePrefix)
        .thenComparing(Ranked::youngestFirst, ProposalRanking::comparePrefix)
        .thenComparingInt(ranked -> ranked.option().lotCount())
        .thenComparingLong(ranked -> Math.abs(ranked.units() - target))
        .thenComparing(ranked -> ranked.units() < target)
        .thenComparing(Ranked::pieceKey);
  }

  /** In order, lower first; a list that is a prefix of the other ties. */
  static int comparePrefix(List<Integer> left, List<Integer> right) {
    for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
      int compared = Integer.compare(left.get(i), right.get(i));
      if (compared != 0) {
        return compared;
      }
    }
    return 0;
  }

  /** The same pieces give the same key, whichever lots and order they came in. */
  static String pieceKey(List<WholePieceSearch.LotTake> takes) {
    String pieces =
        takes.stream()
            .flatMap(take -> take.pieceIds().stream())
            .map(UUID::toString)
            .sorted()
            .collect(Collectors.joining(","));
    return EvidenceFingerprint.sha256(pieces).substring(0, 16);
  }
}
