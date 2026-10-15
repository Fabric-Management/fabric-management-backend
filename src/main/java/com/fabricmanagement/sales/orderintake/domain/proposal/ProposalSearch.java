package com.fabricmanagement.sales.orderintake.domain.proposal;

import com.fabricmanagement.inventory.lothealth.domain.LotHealthPolicy;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch.Bits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The whole-piece searches behind a stock proposal (STOCK-PROPOSAL-2; Fatih, 2026-10-12 to
 * 2026-10-14). A lot is either closed (every pool piece taken) or keeps at least its lot-health
 * minimum of normal pieces ({@link LotHealthPolicy}, three by default); short pieces do not count,
 * and a lot already under the minimum is closed or left alone. A computed selection never leaves
 * fewer behind. Among selections the tenant comes first (Fatih, 2026-10-14): a remnant lot closed,
 * more lots closed, completing from the lot with fewer pieces (measured by the deepest lot taken
 * from partly), older stock measured by the youngest lot used (an added lot never makes a selection
 * deeper or older), then fewer lots.
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
 * (at least the minimum of normal pieces left). The searches walk these lot patterns depth first
 * and check each against the totals its lots reach together (bitset sums). They are exact, because
 * every allowed selection belongs to one pattern. Among selections that rank the same the first
 * found wins; the choice is deterministic. They refuse instead of answering approximately: above
 * {@link WholePieceSearch#MAX_LOTS}, {@link WholePieceSearch#MAX_PIECES_PER_LOT} or {@link
 * WholePieceSearch#MAX_UNITS}, or when the pattern or sum budget runs out.
 */
public final class ProposalSearch {

  static final long PATTERN_BUDGET = 2_000_000L;
  static final long SUM_BUDGET = 50_000_000L;

  private ProposalSearch() {}

  /**
   * A lot with its age and the normal pieces it must keep when it is not closed. {@code ageRank}
   * orders production dates: lower is older, equal dates share a rank and an unknown date ranks
   * last.
   */
  public record SearchLot(WholePieceSearch.Lot lot, int ageRank, int minKeep) {
    public SearchLot {
      if (minKeep < 1) {
        throw new IllegalArgumentException("A lot keeps at least one piece when it is not closed");
      }
    }

    /** Already under the minimum of normal pieces: closed or left alone, never taken from. */
    public boolean remnant() {
      return lot.normalPieces() < minKeep;
    }

    /** With the standard lot-health minimum. */
    public SearchLot(WholePieceSearch.Lot lot, int ageRank) {
      this(lot, ageRank, LotHealthPolicy.STANDARD.minHealthyPieces());
    }
  }

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

  /**
   * A lot with its reachable totals per use, in normalised units. {@code normalOnly} are the sums
   * of exactly k normal pieces; {@code withShort} the same with any of the short pieces.
   */
  private record Prepared(
      WholePieceSearch.Lot lot,
      UUID id,
      int ageRank,
      int minKeep,
      boolean remnant,
      Bits[] normalOnly,
      Bits[] withShort,
      Bits closed,
      Bits partial) {

    int pieces() {
      return lot.pieces().size();
    }

    int normals() {
      return lot.normalPieces();
    }

    Bits totals(Use use) {
      return use == Use.CLOSED ? closed : partial;
    }

    /** How many normal pieces a take of {@code total} needs; the fewest, so more stay. */
    int normalsTaken(Use use, int total) {
      if (use == Use.CLOSED) {
        return normals();
      }
      for (int k = 0; k <= normals() - minKeep; k++) {
        if (withShort[k].get(total)) {
          return k;
        }
      }
      throw new IllegalStateException("Proposal reconstruction found no piece count");
    }
  }

  /**
   * What the ranking compares (Fatih, 2026-10-14); which parts lead depends on the goal. {@code
   * partialSizes} are the pool sizes of the lots taken from partly, largest first; {@code
   * youngestFirst} the age ranks of the lots used, youngest first. Both compare in order, lower
   * first, and a list that is a prefix of the other ties: an added lot never counts for or against
   * on its own, so a selection is as deep as its deepest partial lot and as old as its youngest
   * lot.
   */
  private record Key(
      int total,
      int remnantClosed,
      int closed,
      int[] partialSizes,
      int[] youngestFirst,
      int lots,
      long distance,
      boolean below) {

    int compareTo(Key other, Goal goal) {
      if (goal == Goal.AT_LEAST && total != other.total) {
        return Integer.compare(total, other.total);
      }
      if (goal == Goal.AT_MOST && total != other.total) {
        return Integer.compare(other.total, total);
      }
      if (remnantClosed != other.remnantClosed) {
        return Integer.compare(other.remnantClosed, remnantClosed);
      }
      if (closed != other.closed) {
        return Integer.compare(other.closed, closed);
      }
      int compared = comparePrefix(partialSizes, other.partialSizes);
      if (compared != 0) {
        return compared;
      }
      compared = comparePrefix(youngestFirst, other.youngestFirst);
      if (compared != 0) {
        return compared;
      }
      if (lots != other.lots) {
        return Integer.compare(lots, other.lots);
      }
      if (distance != other.distance) {
        return Long.compare(distance, other.distance);
      }
      return Boolean.compare(below, other.below);
    }

    /** In order, lower first; a prefix ties. */
    static int comparePrefix(int[] left, int[] right) {
      for (int i = 0; i < Math.min(left.length, right.length); i++) {
        if (left[i] != right[i]) {
          return Integer.compare(left[i], right[i]);
        }
      }
      return 0;
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
    walk.walk(0, start, 0, 0, 0);
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
                .map(
                    piece ->
                        new WholePieceSearch.Piece(
                            piece.id(), piece.units() / scale, piece.shortPiece()))
                .toList(),
            original.capacity() == WholePieceSearch.Lot.UNLIMITED
                ? WholePieceSearch.Lot.UNLIMITED
                : original.capacity() / scale);
    Bits[] normalOnly = WholePieceSearch.normalCountSums(lot, size, false);
    Bits[] withShort = WholePieceSearch.normalCountSums(lot, size, true);
    Bits closed = new Bits(size);
    long total = lot.total();
    if (total < size && total <= lot.capacity()) {
      closed.set((int) total);
    }
    // Partly: at least the lot-health minimum of normal pieces stays, never fewer; any short
    // pieces may go with them. A lot already under the minimum is never taken from.
    Bits partial = new Bits(size);
    for (int k = 0; k <= lot.normalPieces() - source.minKeep(); k++) {
      partial.or(withShort[k]);
    }
    partial.clear(0);
    if (lot.capacity() < size - 1) {
      partial.clearFrom((int) lot.capacity() + 1);
    }
    return new Prepared(
        lot,
        original.id(),
        source.ageRank(),
        source.minKeep(),
        source.remnant(),
        normalOnly,
        withShort,
        closed,
        partial);
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
    private final int[] remnantClosableFrom;
    private final Use[] uses;
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
      this.closableFrom = new int[lots.size() + 1];
      this.remnantClosableFrom = new int[lots.size() + 1];
      for (int i = lots.size() - 1; i >= 0; i--) {
        boolean closable = !lots.get(i).closed().isEmpty();
        closableFrom[i] = closableFrom[i + 1] + (closable ? 1 : 0);
        remnantClosableFrom[i] =
            remnantClosableFrom[i + 1] + (closable && lots.get(i).remnant() ? 1 : 0);
      }
    }

    void walk(int index, Bits reach, int remnantClosed, int closed, int used) {
      if (--patterns < 0) {
        throw new WholePieceSearch.SearchBudgetExceeded();
      }
      if (reach.isEmpty()) {
        return;
      }
      // In a window the closed lots lead the ranking, remnant lots first: a pattern that cannot
      // reach the best's closed lots cannot beat it.
      if (goal == Goal.WINDOW && best != null) {
        int remnantAtMost = remnantClosed + remnantClosableFrom[index];
        if (remnantAtMost < best.remnantClosed()
            || (remnantAtMost == best.remnantClosed()
                && closed + closableFrom[index] < best.closed())) {
          return;
        }
      }
      if (index == lots.size()) {
        if (used > 0) {
          consider(reach, remnantClosed, closed, used);
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
        boolean closes = use == Use.CLOSED;
        walk(
            index + 1,
            next,
            remnantClosed + (closes && lot.remnant() ? 1 : 0),
            closed + (closes ? 1 : 0),
            used + 1);
      }
      uses[index] = null;
      walk(index + 1, reach, remnantClosed, closed, used);
    }

    /** A complete pattern: the total its goal asks for, if the pattern reaches one. */
    private void consider(Bits reach, int remnantClosed, int closed, int used) {
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
      int[] partialSizes = new int[used - closed];
      int[] youngestFirst = new int[used];
      int partials = 0;
      int ranked = 0;
      for (int i = 0; i < lots.size(); i++) {
        if (uses[i] == null) {
          continue;
        }
        youngestFirst[ranked++] = lots.get(i).ageRank();
        if (uses[i] == Use.PARTIAL) {
          partialSizes[partials++] = lots.get(i).pieces();
        }
      }
      Arrays.sort(partialSizes);
      reverse(partialSizes);
      Arrays.sort(youngestFirst);
      reverse(youngestFirst);
      Key key =
          new Key(
              total,
              remnantClosed,
              closed,
              partialSizes,
              youngestFirst,
              used,
              Math.abs(total - target),
              total < target);
      if (best == null || key.compareTo(best, goal) < 0) {
        best = key;
        bestUses = uses.clone();
      }
    }

    private static void reverse(int[] values) {
      for (int i = 0, j = values.length - 1; i < j; i++, j--) {
        int swap = values[i];
        values[i] = values[j];
        values[j] = swap;
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
      int normals = lot.normalsTaken(use, chosen);
      List<UUID> pieceIds =
          WholePieceSearch.pickPieces(lot.lot(), lot.normalOnly(), normals, chosen);
      takes.add(
          new WholePieceSearch.LotTake(
              lot.id(), pieceIds, chosen * scale, lot.pieces() - pieceIds.size()));
      remaining -= chosen;
    }
    if (remaining != 0) {
      throw new IllegalStateException("Proposal reconstruction did not close");
    }
    Collections.reverse(takes);
    return new WholePieceSearch.Selection((long) total * scale, takes);
  }
}
