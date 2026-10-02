package com.fabricmanagement.sales.orderintake.domain.proposal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Exact whole-piece search (SOI IK-07). Quantities are integer units (for example centimetres).
 * Pieces are never cut. A lot from which at least one piece is taken must not be left with exactly
 * one eligible piece unless {@code allowRemnant} is set; lots already holding a single piece may be
 * emptied. Among selections with the same total the one using fewer lots wins; among those, lots
 * earlier in the given order (older stock) are preferred.
 *
 * <p>The search is a bitset dynamic programme over (lot, pieces taken from the lot, lots used). It
 * refuses problems above {@link #MAX_PIECES_PER_LOT} or {@link #MAX_UNITS} instead of answering
 * approximately.
 */
public final class WholePieceSearch {

  public static final int MAX_PIECES_PER_LOT = 60;
  public static final int MAX_LOTS = 12;
  public static final long MAX_UNITS = 1_000_000L;
  private static final long DFS_BUDGET = 5_000_000L;

  private WholePieceSearch() {}

  public record Piece(UUID id, long units) {
    public Piece {
      if (id == null || units <= 0) {
        throw new IllegalArgumentException("A piece needs an id and a positive quantity");
      }
    }
  }

  /** A lot and its eligible pieces; lots are passed in preference order (older first). */
  public record Lot(UUID id, List<Piece> pieces) {
    public Lot {
      pieces = List.copyOf(pieces);
    }

    long total() {
      return pieces.stream().mapToLong(Piece::units).sum();
    }
  }

  public record LotTake(UUID lotId, List<UUID> pieceIds, long units, int remainingPieces) {
    public LotTake {
      pieceIds = List.copyOf(pieceIds);
    }

    public boolean leavesSingleRemnant() {
      return remainingPieces == 1;
    }
  }

  public record Selection(long total, List<LotTake> takes) {
    public Selection {
      takes = List.copyOf(takes);
    }

    public int lotCount() {
      return takes.size();
    }

    public boolean leavesSingleRemnant() {
      return takes.stream().anyMatch(LotTake::leavesSingleRemnant);
    }
  }

  /**
   * Exact is the requested total itself; above and below are the nearest totals strictly larger and
   * strictly smaller than the request.
   */
  public record Result(
      Optional<Selection> exact,
      Optional<Selection> above,
      Optional<Selection> below,
      boolean limitExceeded) {

    static Result limit() {
      return new Result(Optional.empty(), Optional.empty(), Optional.empty(), true);
    }
  }

  public static Result search(List<Lot> lots, long target, boolean allowRemnant) {
    long divisor = target;
    for (Lot lot : lots) for (Piece piece : lot.pieces()) divisor = gcd(divisor, piece.units());
    final long scale = Math.max(1, divisor);
    List<Lot> normalized =
        lots.stream()
            .map(
                lot ->
                    new Lot(
                        lot.id(),
                        lot.pieces().stream()
                            .map(piece -> new Piece(piece.id(), piece.units() / scale))
                            .toList()))
            .toList();
    try {
      Result result = searchNormalized(normalized, target / scale, allowRemnant);
      return new Result(
          result.exact().map(value -> restore(value, scale)),
          result.above().map(value -> restore(value, scale)),
          result.below().map(value -> restore(value, scale)),
          result.limitExceeded());
    } catch (SearchBudgetExceeded e) {
      return Result.limit();
    }
  }

  private static long gcd(long a, long b) {
    while (b != 0) {
      long remainder = a % b;
      a = b;
      b = remainder;
    }
    return a;
  }

  private static Selection restore(Selection value, long scale) {
    return new Selection(
        value.total() * scale,
        value.takes().stream()
            .map(
                take ->
                    new LotTake(
                        take.lotId(),
                        take.pieceIds(),
                        take.units() * scale,
                        take.remainingPieces()))
            .toList());
  }

  private static final class SearchBudgetExceeded extends RuntimeException {}

  private static Result searchNormalized(List<Lot> lots, long target, boolean allowRemnant) {
    if (target <= 0) {
      throw new IllegalArgumentException("Target must be positive");
    }
    List<Lot> usable = lots.stream().filter(lot -> !lot.pieces().isEmpty()).toList();
    if (usable.isEmpty()) {
      return new Result(Optional.empty(), Optional.empty(), Optional.empty(), false);
    }
    if (usable.size() > MAX_LOTS
        || target > MAX_UNITS
        || usable.stream().anyMatch(lot -> lot.pieces().size() > MAX_PIECES_PER_LOT)) {
      return Result.limit();
    }
    long runTotal = usable.stream().mapToLong(Lot::total).sum();
    long cap = Math.min(runTotal, MAX_UNITS);
    boolean truncated = runTotal > MAX_UNITS;
    int size = (int) cap + 1;
    int lotCount = usable.size();

    // Per lot: sums reachable by taking exactly c pieces, and the union over allowed c >= 1.
    List<Bits[]> perCount = new ArrayList<>(lotCount);
    List<Bits> allowedUnion = new ArrayList<>(lotCount);
    for (Lot lot : usable) {
      Bits[] byCount = countSums(lot, size);
      perCount.add(byCount);
      Bits union = new Bits(size);
      int n = lot.pieces().size();
      for (int c = 1; c <= n; c++) {
        if (allowed(n, c, allowRemnant)) {
          union.or(byCount[c]);
        }
      }
      allowedUnion.add(union);
    }

    long[] convolutionBudget = {50_000_000L};
    // Global: stages[i][l] = totals reachable with lots 0..i-1 using exactly l lots.
    Bits[][] stages = new Bits[lotCount + 1][];
    stages[0] = emptyStage(lotCount, size);
    stages[0][0].set(0);
    for (int i = 0; i < lotCount; i++) {
      Bits[] previous = stages[i];
      Bits[] next = emptyStage(lotCount, size);
      Bits union = allowedUnion.get(i);
      for (int l = 0; l <= lotCount; l++) {
        next[l].or(previous[l]);
        if (l > 0 && !previous[l - 1].isEmpty() && !union.isEmpty()) {
          sumset(next[l], previous[l - 1], union, convolutionBudget);
        }
      }
      stages[i + 1] = next;
    }

    Bits[] last = stages[lotCount];
    Optional<Selection> exact = Optional.empty();
    int exactLots = fewestLots(last, (int) target);
    if (target <= cap && exactLots > 0) {
      exact =
          Optional.of(
              reconstruct(
                  usable, perCount, allowedUnion, stages, (int) target, exactLots, allowRemnant));
    }
    Optional<Selection> above = Optional.empty();
    int aboveTotal =
        target + 1 > cap ? -1 : firstReachable(last, (int) (target + 1), size - 1, true);
    if (aboveTotal > target) {
      above =
          Optional.of(
              reconstruct(
                  usable,
                  perCount,
                  allowedUnion,
                  stages,
                  aboveTotal,
                  fewestLots(last, aboveTotal),
                  allowRemnant));
    }
    Optional<Selection> below = Optional.empty();
    int belowTotal = firstReachable(last, 1, (int) Math.min(target - 1, cap), false);
    if (belowTotal > 0) {
      below =
          Optional.of(
              reconstruct(
                  usable,
                  perCount,
                  allowedUnion,
                  stages,
                  belowTotal,
                  fewestLots(last, belowTotal),
                  allowRemnant));
    }
    boolean limitExceeded = truncated && above.isEmpty();
    return new Result(exact, above, below, limitExceeded);
  }

  static boolean allowed(int piecesInLot, int taken, boolean allowRemnant) {
    if (taken < 1 || taken > piecesInLot) {
      return false;
    }
    return allowRemnant || piecesInLot - taken != 1;
  }

  private static Bits[] countSums(Lot lot, int size) {
    int n = lot.pieces().size();
    Bits[] byCount = new Bits[n + 1];
    for (int c = 0; c <= n; c++) {
      byCount[c] = new Bits(size);
    }
    byCount[0].set(0);
    int processed = 0;
    for (Piece piece : lot.pieces()) {
      for (int c = processed; c >= 0; c--) {
        byCount[c + 1].orShifted(byCount[c], piece.units());
      }
      processed++;
    }
    return byCount;
  }

  private static Bits[] emptyStage(int lotCount, int size) {
    Bits[] stage = new Bits[lotCount + 1];
    for (int l = 0; l <= lotCount; l++) {
      stage[l] = new Bits(size);
    }
    return stage;
  }

  /** target |= a (+) b, limited to the bitset size; iterates the sparser operand. */
  private static void sumset(Bits target, Bits a, Bits b, long[] budget) {
    Bits sparse = a.cardinality() <= b.cardinality() ? a : b;
    Bits dense = sparse == a ? b : a;
    for (int s = sparse.nextSetBit(0); s >= 0; s = sparse.nextSetBit(s + 1)) {
      budget[0] -= dense.words.length;
      if (budget[0] < 0) throw new SearchBudgetExceeded();
      target.orShifted(dense, s);
    }
  }

  /** The smallest number of lots (>= 1) with which {@code total} is reachable, or -1. */
  private static int fewestLots(Bits[] last, int total) {
    for (int l = 1; l < last.length; l++) {
      if (last[l].get(total)) {
        return l;
      }
    }
    return -1;
  }

  /** First reachable total scanning upward from {@code from} or downward from {@code to}. */
  private static int firstReachable(Bits[] last, int from, int to, boolean upward) {
    if (from > to) {
      return -1;
    }
    if (upward) {
      for (int total = from; total <= to; total++) {
        if (reachableWithAnyLot(last, total)) {
          return total;
        }
      }
    } else {
      for (int total = to; total >= from; total--) {
        if (reachableWithAnyLot(last, total)) {
          return total;
        }
      }
    }
    return -1;
  }

  private static boolean reachableWithAnyLot(Bits[] last, int total) {
    for (int l = 1; l < last.length; l++) {
      if (last[l].get(total)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Walks back from the youngest lot, skipping a lot whenever the total is reachable without it, so
   * older lots are used first.
   */
  private static Selection reconstruct(
      List<Lot> lots,
      List<Bits[]> perCount,
      List<Bits> allowedUnion,
      Bits[][] stages,
      int total,
      int lotsUsed,
      boolean allowRemnant) {
    List<LotTake> takes = new ArrayList<>();
    int remaining = total;
    int lotsLeft = lotsUsed;
    for (int i = lots.size() - 1; i >= 0; i--) {
      Bits[] before = stages[i];
      if (before[lotsLeft].get(remaining)) {
        continue;
      }
      Bits union = allowedUnion.get(i);
      int chosenSum = -1;
      for (int s = union.nextSetBit(1); s >= 0 && s <= remaining; s = union.nextSetBit(s + 1)) {
        if (lotsLeft >= 1 && before[lotsLeft - 1].get(remaining - s)) {
          chosenSum = s;
          break;
        }
      }
      if (chosenSum < 0) {
        throw new IllegalStateException("Whole-piece reconstruction lost its path");
      }
      Lot lot = lots.get(i);
      Bits[] byCount = perCount.get(i);
      int n = lot.pieces().size();
      int chosenCount = -1;
      for (int c = 1; c <= n; c++) {
        if (allowed(n, c, allowRemnant) && byCount[c].get(chosenSum)) {
          chosenCount = c;
          break;
        }
      }
      if (chosenCount < 0) {
        throw new IllegalStateException("Whole-piece reconstruction found no piece count");
      }
      List<UUID> pieceIds = pickPieces(lot, chosenCount, chosenSum);
      takes.add(new LotTake(lot.id(), pieceIds, chosenSum, n - chosenCount));
      remaining -= chosenSum;
      lotsLeft--;
    }
    if (remaining != 0 || lotsLeft != 0) {
      throw new IllegalStateException("Whole-piece reconstruction did not close");
    }
    Collections.reverse(takes);
    return new Selection(total, takes);
  }

  /** Depth-first choice of exactly {@code count} pieces summing to {@code sum}, bounded. */
  static List<UUID> pickPieces(Lot lot, int count, long sum) {
    List<Piece> sorted =
        lot.pieces().stream()
            .sorted(Comparator.comparingLong(Piece::units).reversed().thenComparing(Piece::id))
            .toList();
    int n = sorted.size();
    long[] values = sorted.stream().mapToLong(Piece::units).toArray();
    long[] suffix = new long[n + 1];
    for (int j = n - 1; j >= 0; j--) {
      suffix[j] = suffix[j + 1] + values[j];
    }
    List<Integer> chosen = new ArrayList<>();
    Set<String> failed = new HashSet<>();
    long[] budget = {DFS_BUDGET};
    if (!dfs(values, suffix, 0, count, sum, chosen, failed, budget)) {
      throw new SearchBudgetExceeded();
    }
    return chosen.stream().map(index -> sorted.get(index).id()).sorted().toList();
  }

  private static boolean dfs(
      long[] values,
      long[] suffix,
      int index,
      int count,
      long sum,
      List<Integer> chosen,
      Set<String> failed,
      long[] budget) {
    if (count == 0) {
      return sum == 0;
    }
    int remainingPieces = values.length - index;
    if (remainingPieces < count || sum <= 0) {
      return false;
    }
    // values are sorted descending: the largest possible take is the next `count` pieces, the
    // smallest the last `count` pieces.
    long max = suffix[index] - suffix[index + count];
    long min = suffix[values.length - count];
    if (sum > max || sum < min) {
      return false;
    }
    String key = index + ":" + count + ":" + sum;
    if (failed.contains(key)) {
      return false;
    }
    if (--budget[0] < 0) {
      throw new SearchBudgetExceeded();
    }
    chosen.add(index);
    if (dfs(values, suffix, index + 1, count - 1, sum - values[index], chosen, failed, budget)) {
      return true;
    }
    chosen.removeLast();
    if (dfs(values, suffix, index + 1, count, sum, chosen, failed, budget)) {
      return true;
    }
    failed.add(key);
    return false;
  }

  /** Minimal fixed-size bitset with shift-or, sized once per search. */
  static final class Bits {
    private final long[] words;
    private final int size;

    Bits(int size) {
      this.size = size;
      this.words = new long[Math.max(1, (size + 63) >>> 6)];
    }

    void set(int index) {
      if (index >= 0 && index < size) {
        words[index >>> 6] |= 1L << (index & 63);
      }
    }

    boolean get(int index) {
      return index >= 0 && index < size && (words[index >>> 6] & (1L << (index & 63))) != 0;
    }

    boolean isEmpty() {
      for (long word : words) {
        if (word != 0) {
          return false;
        }
      }
      return true;
    }

    int cardinality() {
      int count = 0;
      for (long word : words) {
        count += Long.bitCount(word);
      }
      return count;
    }

    void or(Bits other) {
      for (int k = 0; k < words.length; k++) {
        words[k] |= other.words[k];
      }
    }

    /** this |= (other shifted left by {@code shift}), truncated to this size. */
    void orShifted(Bits other, long shift) {
      if (shift < 0 || shift >= size) {
        return;
      }
      int wordShift = (int) (shift >>> 6);
      int bitShift = (int) (shift & 63);
      for (int k = words.length - 1; k >= wordShift; k--) {
        int source = k - wordShift;
        long value = other.words[source] << bitShift;
        if (bitShift != 0 && source > 0) {
          value |= other.words[source - 1] >>> (64 - bitShift);
        }
        words[k] |= value;
      }
      clearTail();
    }

    int nextSetBit(int from) {
      if (from < 0) {
        from = 0;
      }
      if (from >= size) {
        return -1;
      }
      int wordIndex = from >>> 6;
      long word = words[wordIndex] & (-1L << (from & 63));
      while (true) {
        if (word != 0) {
          int index = (wordIndex << 6) + Long.numberOfTrailingZeros(word);
          return index < size ? index : -1;
        }
        if (++wordIndex == words.length) {
          return -1;
        }
        word = words[wordIndex];
      }
    }

    private void clearTail() {
      int extra = words.length * 64 - size;
      if (extra > 0) {
        words[words.length - 1] &= -1L >>> extra;
      }
    }
  }
}
