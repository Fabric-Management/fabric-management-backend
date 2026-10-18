package com.fabricmanagement.inventory.lothealth.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A tenant's lot-health thresholds (Fatih, 2026-10-14): how many normal pieces a lot must keep at
 * each product speed, and when a piece is short. Administrators set them; the defaults are 3, 5 and
 * 8 pieces and 51%.
 *
 * @param minHealthyPiecesMoving normal pieces a lot keeps while the product sells as before
 * @param minHealthyPiecesSlowing normal pieces a lot keeps while it sells less
 * @param minHealthyPiecesStalled normal pieces a lot keeps once it hardly sells
 * @param shortPieceRatio a piece under this share of the lot's average piece is short
 */
public record LotHealthSettings(
    int minHealthyPiecesMoving,
    int minHealthyPiecesSlowing,
    int minHealthyPiecesStalled,
    BigDecimal shortPieceRatio,
    int fabricRollMinHealthyPieces) {

  public static final LotHealthSettings STANDARD =
      new LotHealthSettings(3, 5, 8, new BigDecimal("0.51"));

  public LotHealthSettings(int moving, int slowing, int stalled, BigDecimal ratio) {
    this(moving, slowing, stalled, ratio, 4);
  }

  public LotHealthPolicy fabricRollPolicy() {
    return new LotHealthPolicy(fabricRollMinHealthyPieces, shortPieceRatio);
  }

  public LotHealthSettings {
    if (fabricRollMinHealthyPieces < 4 || fabricRollMinHealthyPieces > 50) {
      throw new IllegalArgumentException("Fabric roll minimum must be between 4 and 50");
    }
    Objects.requireNonNull(shortPieceRatio, "shortPieceRatio");
    if (minHealthyPiecesMoving < 1
        || minHealthyPiecesSlowing < minHealthyPiecesMoving
        || minHealthyPiecesStalled < minHealthyPiecesSlowing) {
      throw new IllegalArgumentException(
          "Minimum pieces rise with slower sales: moving <= slowing <= stalled, at least 1");
    }
  }

  /** The policy for a product at the given speed. */
  public LotHealthPolicy policyFor(ProductSpeed speed) {
    Objects.requireNonNull(speed, "speed");
    int minimum =
        switch (speed) {
          case MOVING -> minHealthyPiecesMoving;
          case SLOWING -> minHealthyPiecesSlowing;
          case STALLED -> minHealthyPiecesStalled;
        };
    return new LotHealthPolicy(minimum, shortPieceRatio);
  }
}
