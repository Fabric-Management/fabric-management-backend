package com.fabricmanagement.sales.orderintake.domain.proposal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Whole-piece primitives (SOI IK-07): pieces, lots and their takes in integer units (for example
 * millimetres), the per-lot bitset sums and the bounded choice of pieces for a reachable sum.
 * Pieces are never cut. A lot may carry a capacity: no take from it exceeds that quantity (free
 * stock net of quote intents, STOCK-PROPOSAL-2). The searches live in {@link ProposalSearch}.
 *
 * <p>A piece is normal or short; the caller says which ({@code LotHealthPolicy}, Fatih,
 * 2026-10-14). The sums of a lot are kept by the number of normal pieces taken, with any of its
 * short pieces: what a lot may keep is counted in normal pieces, and short pieces go first. A take
 * only records how many pool pieces it leaves. Limits are refused, never answered approximately:
 * above {@link #MAX_PIECES_PER_LOT} pieces in a lot, {@link #MAX_LOTS} lots or {@link #MAX_UNITS}
 * units.
 */
public final class WholePieceSearch {

  public static final int MAX_PIECES_PER_LOT = 60;
  public static final int MAX_LOTS = 12;
  public static final long MAX_UNITS = 1_000_000L;
  private static final long DFS_BUDGET = 5_000_000L;

  private WholePieceSearch() {}

  /** A piece; {@code shortPiece} when it is under the short-piece share of its lot's average. */
  public record Piece(UUID id, long units, boolean shortPiece) {
    public Piece {
      if (id == null || units <= 0) {
        throw new IllegalArgumentException("A piece needs an id and a positive quantity");
      }
    }

    /** A normal piece. */
    public Piece(UUID id, long units) {
      this(id, units, false);
    }
  }

  /**
   * A lot and its pool pieces; lots are passed in preference order (older first). {@code capacity}
   * is the most that may be taken from the lot ({@link #UNLIMITED} when only the pieces bound it).
   */
  public record Lot(UUID id, List<Piece> pieces, long capacity) {
    public static final long UNLIMITED = Long.MAX_VALUE;

    public Lot {
      pieces = List.copyOf(pieces);
      if (capacity < 0) {
        throw new IllegalArgumentException("A lot capacity cannot be negative");
      }
    }

    public Lot(UUID id, List<Piece> pieces) {
      this(id, pieces, UNLIMITED);
    }

    public long total() {
      return pieces.stream().mapToLong(Piece::units).reduce(0L, Math::addExact);
    }

    /** How many of the pieces are normal, not short. */
    public int normalPieces() {
      return (int) pieces.stream().filter(piece -> !piece.shortPiece()).count();
    }

    /** What may be taken from the lot at most. */
    long takeable() {
      return Math.min(total(), capacity);
    }
  }

  public record LotTake(UUID lotId, List<UUID> pieceIds, long units, int remainingPieces) {
    public LotTake {
      pieceIds = List.copyOf(pieceIds);
    }
  }

  public record Selection(long total, List<LotTake> takes) {
    public Selection {
      takes = List.copyOf(takes);
    }

    public int lotCount() {
      return takes.size();
    }
  }

  static long gcd(long a, long b) {
    while (b != 0) {
      long remainder = a % b;
      a = b;
      b = remainder;
    }
    return a;
  }

  static final class SearchBudgetExceeded extends RuntimeException {}

  /**
   * The sums of exactly {@code k} of the lot's normal pieces, for every k, when {@code withShort}
   * is false; with any of its short pieces added when it is true.
   */
  static Bits[] normalCountSums(Lot lot, int size, boolean withShort) {
    List<Piece> normal = lot.pieces().stream().filter(piece -> !piece.shortPiece()).toList();
    int n = normal.size();
    Bits[] byCount = new Bits[n + 1];
    for (int c = 0; c <= n; c++) {
      byCount[c] = new Bits(size);
    }
    byCount[0].set(0);
    if (withShort) {
      // Any subset of the short pieces, before a normal piece is counted.
      for (Piece piece : lot.pieces()) {
        if (piece.shortPiece()) {
          byCount[0].orShifted(byCount[0].copy(), piece.units());
        }
      }
    }
    int processed = 0;
    for (Piece piece : normal) {
      for (int c = processed; c >= 0; c--) {
        byCount[c + 1].orShifted(byCount[c], piece.units());
      }
      processed++;
    }
    return byCount;
  }

  /** target |= a (+) b, limited to the bitset size; iterates the sparser operand. */
  static void sumset(Bits target, Bits a, Bits b, long[] budget) {
    Bits sparse = a.cardinality() <= b.cardinality() ? a : b;
    Bits dense = sparse == a ? b : a;
    for (int s = sparse.nextSetBit(0); s >= 0; s = sparse.nextSetBit(s + 1)) {
      budget[0] -= dense.words.length;
      if (budget[0] < 0) throw new SearchBudgetExceeded();
      target.orShifted(dense, s);
    }
  }

  /**
   * The pieces of a take of exactly {@code normalCount} normal pieces summing to {@code sum} with
   * some of the short pieces: short pieces first (Fatih, 2026-10-14), each taken when the rest of
   * the sum stays reachable, smallest first so as many as possible go; then a bounded depth-first
   * choice of the normal pieces. {@code normalSums} are the sums of exactly k normal pieces, by k.
   * The sum must be reachable, as the search found it.
   */
  static List<UUID> pickPieces(Lot lot, Bits[] normalSums, int normalCount, long sum) {
    List<Piece> shortPieces =
        lot.pieces().stream()
            .filter(Piece::shortPiece)
            .sorted(Comparator.comparingLong(Piece::units).thenComparing(Piece::id))
            .toList();
    Bits normal = normalSums[normalCount];
    // Any subset of the short pieces from index i on.
    Bits[] shortSuffix = new Bits[shortPieces.size() + 1];
    shortSuffix[shortPieces.size()] = new Bits(normal.size());
    shortSuffix[shortPieces.size()].set(0);
    for (int i = shortPieces.size() - 1; i >= 0; i--) {
      shortSuffix[i] = shortSuffix[i + 1].copy();
      shortSuffix[i].orShifted(shortSuffix[i + 1], shortPieces.get(i).units());
    }
    List<UUID> chosen = new ArrayList<>();
    long remaining = sum;
    for (int i = 0; i < shortPieces.size(); i++) {
      long units = shortPieces.get(i).units();
      if (units <= remaining && reachable(normal, shortSuffix[i + 1], remaining - units)) {
        chosen.add(shortPieces.get(i).id());
        remaining -= units;
      }
    }
    chosen.addAll(pickNormal(lot, normalCount, remaining));
    return chosen.stream().sorted().toList();
  }

  /** {@code need} is some sum of {@code normal} plus some sum of {@code shortLeft}. */
  private static boolean reachable(Bits normal, Bits shortLeft, long need) {
    for (int s = shortLeft.nextSetBit(0); s >= 0 && s <= need; s = shortLeft.nextSetBit(s + 1)) {
      if (normal.get((int) (need - s))) {
        return true;
      }
    }
    return false;
  }

  /** Depth-first choice of exactly {@code count} normal pieces summing to {@code sum}, bounded. */
  private static List<UUID> pickNormal(Lot lot, int count, long sum) {
    List<Piece> sorted =
        lot.pieces().stream()
            .filter(piece -> !piece.shortPiece())
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
    return chosen.stream().map(index -> sorted.get(index).id()).toList();
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

    void clear(int index) {
      if (index >= 0 && index < size) {
        words[index >>> 6] &= ~(1L << (index & 63));
      }
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

    Bits copy() {
      Bits copy = new Bits(size);
      System.arraycopy(words, 0, copy.words, 0, words.length);
      return copy;
    }

    int size() {
      return size;
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

    /** The highest set bit at or below {@code from}, or -1. */
    int previousSetBit(int from) {
      if (from < 0) {
        return -1;
      }
      if (from >= size) {
        from = size - 1;
      }
      int wordIndex = from >>> 6;
      long word = words[wordIndex] & (-1L >>> (63 - (from & 63)));
      while (true) {
        if (word != 0) {
          return (wordIndex << 6) + 63 - Long.numberOfLeadingZeros(word);
        }
        if (--wordIndex < 0) {
          return -1;
        }
        word = words[wordIndex];
      }
    }

    /** Clears every bit at or above {@code from}. */
    void clearFrom(int from) {
      if (from < 0) {
        from = 0;
      }
      if (from >= size) {
        return;
      }
      int wordIndex = from >>> 6;
      words[wordIndex] &= ~(-1L << (from & 63));
      for (int k = wordIndex + 1; k < words.length; k++) {
        words[k] = 0;
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
