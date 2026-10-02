package com.fabricmanagement.sales.orderintake.domain.proposal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One whole-piece quantity option (SOI R08–R11). Quantities are in the line unit; canonical
 * quantities are in the stock's canonical unit (metre or kilogram). Never a reservation.
 */
public record QuantityOption(
    String optionKey,
    OptionKind kind,
    Compatibility compatibility,
    BigDecimal quantity,
    BigDecimal canonicalQuantity,
    BigDecimal difference,
    BigDecimal differencePercent,
    boolean outsideAgreedTolerance,
    List<LotPart> lots) {

  public QuantityOption {
    lots = List.copyOf(lots);
  }

  public int lotCount() {
    return lots.size();
  }

  public boolean leavesSingleRemnant() {
    return lots.stream().anyMatch(LotPart::singleRemnant);
  }

  /** Some used lot may be left with a single piece: pieces of unknown state decide (SOI §3.1). */
  public boolean remnantUnknown() {
    return lots.stream().anyMatch(LotPart::remnantUnknown);
  }

  /** The remnant warning must be acknowledged when a single remnant is certain or unknown. */
  public boolean needsRemnantAcknowledgement() {
    return leavesSingleRemnant() || remnantUnknown();
  }

  public List<UUID> pieceIds() {
    return lots.stream().flatMap(lot -> lot.pieceIds().stream()).toList();
  }

  public List<UUID> batchIds() {
    return lots.stream().map(LotPart::batchId).toList();
  }

  /** What the option is relative to the request. */
  @io.swagger.v3.oas.annotations.media.Schema(name = "QuantityOptionKind", enumAsRef = true)
  public enum OptionKind {
    EXACT,
    ABOVE,
    BELOW,
    /** The requested quantity, reachable only by leaving a single piece in a used lot (SOI A01). */
    REQUESTED_WITH_REMNANT
  }

  /** Why several lots may (or may not yet) ship together (SOI A02, A04, A04-b). */
  @io.swagger.v3.oas.annotations.media.Schema(
      name = "QuantityOptionCompatibility",
      enumAsRef = true)
  public enum Compatibility {
    SINGLE_LOT,
    CONFIRMED,
    TONE_ACCEPTED,
    /** Not a definite option until technical confirmation or a concrete tone acceptance. */
    PENDING_CONFIRMATION
  }

  public record LotPart(
      UUID batchId,
      String lotNo,
      List<UUID> pieceIds,
      BigDecimal quantity,
      int remainingEligiblePieces,
      int unknownPieces,
      boolean singleRemnant,
      UUID remnantPieceId,
      BigDecimal remnantQuantity) {

    public LotPart {
      pieceIds = List.copyOf(pieceIds);
    }

    /** The lot is emptied of eligible pieces and holds no piece of unknown state. */
    public boolean exhausted() {
      return remainingEligiblePieces == 0 && unknownPieces == 0;
    }

    /**
     * At most one eligible piece stays and the lot holds pieces of unknown state: whether a single
     * piece is left cannot be said (SOI §3.1 SINGLE_REMNANT_UNKNOWN).
     */
    public boolean remnantUnknown() {
      return !singleRemnant && unknownPieces > 0 && remainingEligiblePieces <= 1;
    }
  }
}
