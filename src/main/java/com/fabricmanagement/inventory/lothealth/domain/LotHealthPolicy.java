package com.fabricmanagement.inventory.lothealth.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collection;
import java.util.Objects;

/**
 * What a lot may keep after a take (Fatih, 2026-10-14): it is closed, or it keeps at least {@code
 * minHealthyPieces} normal pieces. A short piece, under {@code shortPieceRatio} of the lot's
 * average piece, is not counted: 40 + 30 + 55 m left of a lot whose pieces average 100 m is one
 * normal piece, a remnant; 100 + 100 + 100 + 30 m is three, healthy.
 *
 * <p>Pure: no Spring and no persistence, so the proposal, the warehouse, shipping and reservations
 * give the same answer. Quantities are in one unit per lot (metre, kilogram, pieces of a kind);
 * pieces of different lots are never compared.
 *
 * @param minHealthyPieces the fewest normal pieces a lot may keep when it is not closed
 * @param shortPieceRatio a piece under this share of the lot's average piece is short (0.51 = 51%)
 */
public record LotHealthPolicy(int minHealthyPieces, BigDecimal shortPieceRatio) {

  /** Three normal pieces; a piece under 51% of the average is short. */
  public static final LotHealthPolicy STANDARD = new LotHealthPolicy(3, new BigDecimal("0.51"));

  public LotHealthPolicy {
    if (minHealthyPieces < 1) {
      throw new IllegalArgumentException(
          "A lot must keep at least one piece when it is not closed");
    }
    Objects.requireNonNull(shortPieceRatio, "shortPieceRatio");
    if (shortPieceRatio.signum() <= 0 || shortPieceRatio.compareTo(BigDecimal.ONE) >= 0) {
      throw new IllegalArgumentException("The short-piece ratio lies strictly between 0 and 1");
    }
  }

  /**
   * By count alone: the lot keeps some pieces, but fewer than the minimum. The search uses this
   * where it reasons about counts before it picks pieces.
   */
  public boolean leavesRemnant(int remainingPieces) {
    return remainingPieces > 0 && remainingPieces < minHealthyPieces;
  }

  /** A piece under the short-piece share of the lot's average piece. */
  public boolean isShort(BigDecimal piece, BigDecimal averagePiece) {
    Objects.requireNonNull(piece, "piece");
    Objects.requireNonNull(averagePiece, "averagePiece");
    if (averagePiece.signum() <= 0) {
      return false;
    }
    return piece.compareTo(averagePiece.multiply(shortPieceRatio)) < 0;
  }

  /** How many of the pieces are not short. */
  public int normalPieces(Collection<BigDecimal> pieces, BigDecimal averagePiece) {
    return (int) pieces.stream().filter(piece -> !isShort(piece, averagePiece)).count();
  }

  /** The lot is closed, or what it keeps holds at least the minimum of normal pieces. */
  public boolean healthyRemainder(Collection<BigDecimal> remaining, BigDecimal averagePiece) {
    return remaining.isEmpty() || normalPieces(remaining, averagePiece) >= minHealthyPieces;
  }

  /**
   * The average piece of a lot: total ÷ count. Callers pass the lot as produced when they know it,
   * so the average does not fall as normal pieces are sold; otherwise the pieces at hand.
   *
   * @return null when there are no pieces
   */
  public static BigDecimal averagePiece(Collection<BigDecimal> pieces) {
    if (pieces.isEmpty()) {
      return null;
    }
    BigDecimal total = pieces.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return total.divide(BigDecimal.valueOf(pieces.size()), MathContext.DECIMAL64);
  }
}
