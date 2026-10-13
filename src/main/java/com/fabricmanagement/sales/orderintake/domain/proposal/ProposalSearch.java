package com.fabricmanagement.sales.orderintake.domain.proposal;

import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch.Bits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The whole-piece searches behind a stock proposal (STOCK-PROPOSAL-2; Fatih, 2026-10-12 and
 * 2026-10-13). A lot is either closed (every pool piece taken) or keeps at least three pieces: a
 * computed selection never leaves one or two pieces behind. Among selections the tenant comes
 * first: more lots closed, then older stock, then fewer lots.
 *
 * <ul>
 *   <li>{@link #bestWithin}: the best selection by that ranking whose total lies in a window; among
 *       equals, the total nearest the target, covering it on a tie.
 *   <li>{@link #smallestFrom}: the smallest total at or above a bound; among equal totals, the
 *       ranking.
 *   <li>{@link #largestUpTo}: the largest total at or below a bound; among equal totals, the
 *       ranking.
 * </ul>
 *
 * <p>Every ranking criterion depends only on how each lot is used: not at all, closed, or partly
 * (three or more pieces left). The searches walk these lot patterns depth first and check each
 * against the totals its lots reach together (bitset sums). They are exact, because every allowed
 * selection belongs to one pattern. Among selections that rank the same the first found wins; the
 * choice is deterministic. They refuse instead of answering approximately: above {@link
 * WholePieceSearch#MAX_LOTS}, {@link WholePieceSearch#MAX_PIECES_PER_LOT} or {@link
 * WholePieceSearch#MAX_UNITS}, or when the pattern or sum budget runs out.
 */
public final class ProposalSearch {

  static final long PATTERN_BUDGET = 2_000_000L;
  static final long SUM_BUDGET = 50_000_000L;

  private ProposalSearch() {}

  /**
   * A lot with its age. {@code ageRank} orders production dates: lower is older, equal dates share
   * a rank and an unknown date ranks last.
   */
  public record SearchLot(WholePieceSearch.Lot lot, int ageRank) {}

  /** The best selection, if any, or a reached limit. */
  public record Result(Optional<WholePieceSearch.Selection> best, boolean limitExceeded) {
    static Result none() {
      return new Result(Optional.empty(), false);
    }

    static Result limit() {
      return new Result(Optional.empty(), true);
    }
  }

  /**
   * The best selection by the ranking with a total in {@code [low, high]}; among equals, the total
   * nearest {@code target} (which may lie outside the window), covering it on a tie.
   */
  public static Result bestWithin(List<SearchLot> lots, long low, long high, long target) {
    if (target <= 0 || low > high) {
      throw new IllegalArgumentException("A window needs a positive target and low <= high");
    }
    return run(lots, Goal.WINDOW, Math.max(1, low), high, target);
  }

  /** The smallest total at or above {@code bound}; among equal totals, the best by the ranking. */
  public static Result smallestFrom(List<SearchLot> lots, long bound) {
    return run(lots, Goal.AT_LEAST, Math.max(1, bound), 0, 0);
  }

  /** The largest total at or below {@code bound}; among equal totals, the best by the ranking. */
  public static Result largestUpTo(List<SearchLot> lots, long bound) {
    if (bound < 1) {
      return Result.none();
    }
    return run(lots, Goal.AT_MOST, bound, 0, 0);
  }

  private enum Goal {
    WINDOW,
    AT_LEAST,
    AT_MOST
  }

  /** How a lot is used; the order is the order in which the walk tries them. */
  private enum Use {
    CLOSED,
    PARTIAL
  }

  /** A lot with its reachable totals per use, in normalised units. */
  private record Prepared(
      WholePieceSearch.Lot lot, UUID id, int ageRank, Bits[] byCount, Bits closed, Bits partial) {

    int pieces() {
      return lot.pieces().size();
    }

    Bits totals(Use use) {
      return use == Use.CLOSED ? closed : partial;
    }

    int piecesTaken(Use use, int total) {
      int n = pieces();
      if (use == Use.CLOSED) {
        return n;
      }
      for (int c = 1; c <= n - 3; c++) {
        if (byCount[c].get(total)) {
          return c;
        }
      }
      throw new IllegalStateException("Proposal reconstruction found no piece count");
    }
  }

  /** What the ranking compares; which parts lead depends on the goal. */
  private record Key(int total, int closed, int[] ranks, int lots, long distance, boolean below) {

    int compareTo(Key other, Goal goal) {
      if (goal == Goal.AT_LEAST && total != other.total) {
        return Integer.compare(total, other.total);
      }
      if (goal == Goal.AT_MOST && total != other.total) {
        return Integer.compare(other.total, total);
      }
      if (closed != other.closed) {
        return Integer.compare(other.closed, closed);
      }
      for (int i = 0; i < Math.min(ranks.length, other.ranks.length); i++) {
        if (ranks[i] != other.ranks[i]) {
          return Integer.compare(ranks[i], other.ranks[i]);
        }
      }
      if (lots != other.lots) {
        return Integer.compare(lots, other.lots);
      }
      if (distance != other.distance) {
        return Long.compare(distance, other.distance);
      }
      return Boolean.compare(below, other.below);
    }
  }

  private static Result run(List<SearchLot> lots, Goal goal, long first, long second, long target) {
    List<SearchLot> usable =
        lots.stream()
            .filter(lot -> !lot.lot().pieces().isEmpty() && lot.lot().capacity() > 0)
            .toList();
    if (usable.isEmpty()) {
      return Result.none();
    }
    if (usable.size() > WholePieceSearch.MAX_LOTS
        || usable.stream()
            .anyMatch(lot -> lot.lot().pieces().size() > WholePieceSearch.MAX_PIECES_PER_LOT)) {
      return Result.limit();
    }
    long scale = goal == Goal.WINDOW ? target : 0;
    for (SearchLot lot : usable) {
      for (WholePieceSearch.Piece piece : lot.lot().pieces()) {
        scale = WholePieceSearch.gcd(scale, piece.units());
      }
    }
    scale = Math.max(1, scale);
    long takeable = 0;
    for (SearchLot lot : usable) {
      takeable += Math.min(lot.lot().total(), lot.lot().capacity()) / scale;
    }
    long low;
    long high;
    boolean truncated = false;
    switch (goal) {
      case WINDOW -> {
        low = ceilDiv(first, scale);
        high = Math.min(Math.floorDiv(second, scale), takeable);
      }
      case AT_LEAST -> {
        low = ceilDiv(first, scale);
        high = Math.min(takeable, WholePieceSearch.MAX_UNITS);
        truncated = takeable > WholePieceSearch.MAX_UNITS;
      }
      default -> {
        low = 1;
        high = Math.min(Math.floorDiv(first, scale), takeable);
      }
    }
    if (low > high) {
      return truncated ? Result.limit() : Result.none();
    }
    if (high > WholePieceSearch.MAX_UNITS) {
      return Result.limit();
    }
    try {
      Optional<WholePieceSearch.Selection> best =
          searchNormalized(usable, goal, scale, (int) low, (int) high, target / scale);
      if (best.isEmpty() && truncated) {
        return Result.limit();
      }
      return new Result(best, false);
    } catch (WholePieceSearch.SearchBudgetExceeded e) {
      return Result.limit();
    }
  }

  private static long ceilDiv(long value, long divisor) {
    return -Math.floorDiv(-value, divisor);
  }

  private static Optional<WholePieceSearch.Selection> searchNormalized(
      List<SearchLot> lots, Goal goal, long scale, int low, int high, long target) {
    int size = high + 1;
    List<Prepared> prepared = new ArrayList<>();
    for (SearchLot lot : lots) {
      prepared.add(prepare(lot, scale, size));
    }
    long[] sums = {SUM_BUDGET};
    Walk walk = new Walk(prepared, goal, low, high, target, sums);
    Bits start = new Bits(size);
    start.set(0);
    walk.walk(0, start, 0, 0);
    if (walk.best == null) {
      return Optional.empty();
    }
    return Optional.of(reconstruct(prepared, walk.bestUses, walk.best.total(), scale, size, sums));
  }

  private static Prepared prepare(SearchLot source, long scale, int size) {
    WholePieceSearch.Lot original = source.lot();
    WholePieceSearch.Lot lot =
        new WholePieceSearch.Lot(
            original.id(),
            original.pieces().stream()
                .map(piece -> new WholePieceSearch.Piece(piece.id(), piece.units() / scale))
                .toList(),
            original.capacity() == WholePieceSearch.Lot.UNLIMITED
                ? WholePieceSearch.Lot.UNLIMITED
                : original.capacity() / scale);
    int n = lot.pieces().size();
    Bits[] byCount = WholePieceSearch.countSums(lot, size);
    Bits closed = new Bits(size);
    long total = lot.total();
    if (total < size && total <= lot.capacity()) {
      closed.set((int) total);
    }
    // Partly: three or more pieces stay, never one or two.
    Bits partial = new Bits(size);
    for (int c = 1; c <= n - 3; c++) {
      partial.or(byCount[c]);
    }
    if (lot.capacity() < size - 1) {
      partial.clearFrom((int) lot.capacity() + 1);
    }
    return new Prepared(lot, original.id(), source.ageRank(), byCount, closed, partial);
  }

  /** One depth-first walk over the lot patterns. */
  private static final class Walk {
    private final List<Prepared> lots;
    private final Goal goal;
    private final int low;
    private final int high;
    private final long target;
    private final long[] sums;
    private final int[] closableFrom;
    private final Use[] uses;
    private final int[] ranks;
    private long patterns = PATTERN_BUDGET;
    private Key best;
    private Use[] bestUses;

    Walk(List<Prepared> lots, Goal goal, int low, int high, long target, long[] sums) {
      this.lots = lots;
      this.goal = goal;
      this.low = low;
      this.high = high;
      this.target = target;
      this.sums = sums;
      this.uses = new Use[lots.size()];
      this.ranks = new int[lots.size()];
      this.closableFrom = new int[lots.size() + 1];
      for (int i = lots.size() - 1; i >= 0; i--) {
        closableFrom[i] = closableFrom[i + 1] + (lots.get(i).closed().isEmpty() ? 0 : 1);
      }
    }

    void walk(int index, Bits reach, int closed, int used) {
      if (--patterns < 0) {
        throw new WholePieceSearch.SearchBudgetExceeded();
      }
      if (reach.isEmpty()) {
        return;
      }
      // In a window the closed lots lead the ranking: a pattern that cannot reach the best's
      // closed lots cannot beat it.
      if (goal == Goal.WINDOW && best != null && closed + closableFrom[index] < best.closed()) {
        return;
      }
      if (index == lots.size()) {
        if (used > 0) {
          consider(reach, closed, used);
        }
        return;
      }
      Prepared lot = lots.get(index);
      for (Use use : Use.values()) {
        Bits totals = lot.totals(use);
        if (totals.isEmpty()) {
          continue;
        }
        Bits next = new Bits(reach.size());
        WholePieceSearch.sumset(next, reach, totals, sums);
        uses[index] = use;
        ranks[used] = lot.ageRank();
        walk(index + 1, next, closed + (use == Use.CLOSED ? 1 : 0), used + 1);
      }
      uses[index] = null;
      walk(index + 1, reach, closed, used);
    }

    /** A complete pattern: the total its goal asks for, if the pattern reaches one. */
    private void consider(Bits reach, int closed, int used) {
      int total =
          switch (goal) {
            case WINDOW -> nearestInWindow(reach);
            case AT_LEAST -> reach.nextSetBit(low);
            case AT_MOST -> {
              int found = reach.previousSetBit(high);
              yield found >= low ? found : -1;
            }
          };
      if (total < 0) {
        return;
      }
      int[] sorted = Arrays.copyOf(ranks, used);
      Arrays.sort(sorted);
      Key key = new Key(total, closed, sorted, used, Math.abs(total - target), total < target);
      if (best == null || key.compareTo(best, goal) < 0) {
        best = key;
        bestUses = uses.clone();
      }
    }

    /** The total in the window nearest the target, covering it on a tie; -1 when none. */
    private int nearestInWindow(Bits reach) {
      int from = (int) Math.max(low, Math.min(target, (long) high + 1));
      int above = from <= high ? reach.nextSetBit(from) : -1;
      int below = reach.previousSetBit((int) Math.min(high, target - 1));
      if (below < low) {
        below = -1;
      }
      if (above < 0) {
        return below;
      }
      if (below < 0) {
        return above;
      }
      return (target - below) < (above - target) ? below : above;
    }
  }

  /** The pieces of the chosen pattern reaching {@code total}, youngest lot first back to oldest. */
  private static WholePieceSearch.Selection reconstruct(
      List<Prepared> lots, Use[] uses, int total, long scale, int size, long[] sums) {
    Bits[] prefix = new Bits[lots.size() + 1];
    prefix[0] = new Bits(size);
    prefix[0].set(0);
    for (int i = 0; i < lots.size(); i++) {
      if (uses[i] == null) {
        prefix[i + 1] = prefix[i];
      } else {
        prefix[i + 1] = new Bits(size);
        WholePieceSearch.sumset(prefix[i + 1], prefix[i], lots.get(i).totals(uses[i]), sums);
      }
    }
    List<WholePieceSearch.LotTake> takes = new ArrayList<>();
    int remaining = total;
    for (int i = lots.size() - 1; i >= 0; i--) {
      Use use = uses[i];
      if (use == null) {
        continue;
      }
      Prepared lot = lots.get(i);
      Bits totals = lot.totals(use);
      int chosen = -1;
      for (int s = totals.nextSetBit(1); s >= 0 && s <= remaining; s = totals.nextSetBit(s + 1)) {
        if (prefix[i].get(remaining - s)) {
          chosen = s;
          break;
        }
      }
      if (chosen < 0) {
        throw new IllegalStateException("Proposal reconstruction lost its path");
      }
      int count = lot.piecesTaken(use, chosen);
      List<UUID> pieceIds = WholePieceSearch.pickPieces(lot.lot(), count, chosen);
      takes.add(
          new WholePieceSearch.LotTake(lot.id(), pieceIds, chosen * scale, lot.pieces() - count));
      remaining -= chosen;
    }
    if (remaining != 0) {
      throw new IllegalStateException("Proposal reconstruction did not close");
    }
    Collections.reverse(takes);
    return new WholePieceSearch.Selection((long) total * scale, takes);
  }
}
