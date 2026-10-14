package com.fabricmanagement.platform.tenant.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * A tenant's stock-proposal settings (Fatih, 2026-10-14), stored in {@code common_tenant.settings}
 * beside the other tenant settings. Administrators change them; the defaults are the decided
 * values. Percentages are whole percents (5 = 5%).
 *
 * <p>A missing value, from settings saved before it existed, reads as its default, so older tenant
 * settings always load.
 *
 * @param toleranceDownPct the lower tolerance a new order line starts with; the line still requires
 *     both limits
 * @param toleranceUpPct the upper tolerance a new order line starts with
 * @param minHealthyPiecesMoving normal pieces a lot keeps while its product sells as before
 * @param minHealthyPiecesSlowing normal pieces a lot keeps while its product sells less
 * @param minHealthyPiecesStalled normal pieces a lot keeps once its product hardly sells
 * @param shortPiecePct a piece under this percent of its lot's average piece is short
 * @param speedWindowDays the period whose shipments are compared with the one before it
 * @param slowingBelowPct a product ships less than this percent of the period before: slowing
 * @param stalledBelowPct a product ships less than this percent of the period before: stalled
 * @param slowingCoverDays free stock lasting longer than this many days: slowing
 * @param lotCloseExtraSlowingPct how far a lot-closing option may exceed the upper tolerance for a
 *     slowing product
 * @param lotCloseExtraStalledPct the same for a stalled product
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockProposalSettings(
    BigDecimal toleranceDownPct,
    BigDecimal toleranceUpPct,
    Integer minHealthyPiecesMoving,
    Integer minHealthyPiecesSlowing,
    Integer minHealthyPiecesStalled,
    BigDecimal shortPiecePct,
    Integer speedWindowDays,
    BigDecimal slowingBelowPct,
    BigDecimal stalledBelowPct,
    Integer slowingCoverDays,
    BigDecimal lotCloseExtraSlowingPct,
    BigDecimal lotCloseExtraStalledPct)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  public static final BigDecimal DEFAULT_TOLERANCE_PCT = new BigDecimal("5");
  public static final int DEFAULT_MIN_PIECES_MOVING = 3;
  public static final int DEFAULT_MIN_PIECES_SLOWING = 5;
  public static final int DEFAULT_MIN_PIECES_STALLED = 8;
  public static final BigDecimal DEFAULT_SHORT_PIECE_PCT = new BigDecimal("51");
  public static final int DEFAULT_SPEED_WINDOW_DAYS = 90;
  public static final BigDecimal DEFAULT_SLOWING_BELOW_PCT = new BigDecimal("80");
  public static final BigDecimal DEFAULT_STALLED_BELOW_PCT = new BigDecimal("40");
  public static final int DEFAULT_SLOWING_COVER_DAYS = 180;
  public static final BigDecimal DEFAULT_LOT_CLOSE_EXTRA_SLOWING_PCT = new BigDecimal("10");
  public static final BigDecimal DEFAULT_LOT_CLOSE_EXTRA_STALLED_PCT = new BigDecimal("20");

  public StockProposalSettings {
    toleranceDownPct = toleranceDownPct != null ? toleranceDownPct : DEFAULT_TOLERANCE_PCT;
    toleranceUpPct = toleranceUpPct != null ? toleranceUpPct : DEFAULT_TOLERANCE_PCT;
    minHealthyPiecesMoving =
        minHealthyPiecesMoving != null ? minHealthyPiecesMoving : DEFAULT_MIN_PIECES_MOVING;
    minHealthyPiecesSlowing =
        minHealthyPiecesSlowing != null ? minHealthyPiecesSlowing : DEFAULT_MIN_PIECES_SLOWING;
    minHealthyPiecesStalled =
        minHealthyPiecesStalled != null ? minHealthyPiecesStalled : DEFAULT_MIN_PIECES_STALLED;
    shortPiecePct = shortPiecePct != null ? shortPiecePct : DEFAULT_SHORT_PIECE_PCT;
    speedWindowDays = speedWindowDays != null ? speedWindowDays : DEFAULT_SPEED_WINDOW_DAYS;
    slowingBelowPct = slowingBelowPct != null ? slowingBelowPct : DEFAULT_SLOWING_BELOW_PCT;
    stalledBelowPct = stalledBelowPct != null ? stalledBelowPct : DEFAULT_STALLED_BELOW_PCT;
    slowingCoverDays = slowingCoverDays != null ? slowingCoverDays : DEFAULT_SLOWING_COVER_DAYS;
    lotCloseExtraSlowingPct =
        lotCloseExtraSlowingPct != null
            ? lotCloseExtraSlowingPct
            : DEFAULT_LOT_CLOSE_EXTRA_SLOWING_PCT;
    lotCloseExtraStalledPct =
        lotCloseExtraStalledPct != null
            ? lotCloseExtraStalledPct
            : DEFAULT_LOT_CLOSE_EXTRA_STALLED_PCT;
  }

  /** The decided defaults. */
  public static StockProposalSettings defaults() {
    return new StockProposalSettings(
        null, null, null, null, null, null, null, null, null, null, null, null);
  }
}
