package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.StockSource;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The tenant-first ranking of options (STOCK-PROPOSAL-2; Fatih, 2026-10-12 and 2026-10-13):
 * prerequisites met, free stock, no remnant (only a manual pick can leave one), more lots taken
 * whole, older stock, fewer lots, closer to the request, covering the request, then the pieces
 * themselves. In one place, so the order is read and changed in one place.
 */
final class ProposalRanking {

  private ProposalRanking() {}

  /** An option with what the ranking compares. */
  record Ranked(
      QuantityOption option,
      boolean definite,
      StockPools.Tier tier,
      int remnantPenalty,
      int closedLots,
      List<Instant> dates,
      long units,
      String pieceKey) {

    /** The same ranking facts with another option (a placement or reasons added). */
    Ranked withOption(QuantityOption newOption) {
      return new Ranked(
          newOption, definite, tier, remnantPenalty, closedLots, dates, units, pieceKey);
    }
  }

  /** What the ranking compares for an option built from lot takes. */
  static Ranked rank(
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
        option.source() == StockSource.FREE ? StockPools.Tier.FREE : StockPools.Tier.HELD,
        penalty,
        closed,
        dates,
        takes.stream().mapToLong(WholePieceSearch.LotTake::units).sum(),
        pieceKey(takes));
  }

  /** The tenant-first ranking around {@code target} search units. */
  static Comparator<Ranked> tenantFirst(long target) {
    return Comparator.comparing((Ranked ranked) -> !ranked.definite())
        .thenComparing(Ranked::tier)
        .thenComparingInt(Ranked::remnantPenalty)
        .thenComparing(Comparator.comparingInt(Ranked::closedLots).reversed())
        .thenComparing(Ranked::dates, ProposalRanking::compareAges)
        .thenComparingInt(ranked -> ranked.option().lotCount())
        .thenComparingLong(ranked -> Math.abs(ranked.units() - target))
        .thenComparing(ranked -> ranked.units() < target)
        .thenComparing(Ranked::pieceKey);
  }

  /** Ascending quantity; the same quantity by the ranking. Also the nearest at or above first. */
  static Comparator<Ranked> ascending(Comparator<Ranked> tenantFirst) {
    return Comparator.comparingLong(Ranked::units).thenComparing(tenantFirst);
  }

  /** The nearest below the request first, then the ranking. */
  static Comparator<Ranked> descending(Comparator<Ranked> tenantFirst) {
    return Comparator.comparingLong(Ranked::units).reversed().thenComparing(tenantFirst);
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
