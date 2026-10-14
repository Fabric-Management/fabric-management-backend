package com.fabricmanagement.sales.orderintake.domain.proposal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One whole-piece stock proposal option (SOI R08–R11, STOCK-PROPOSAL-2). Quantities are in the line
 * unit; canonical quantities are in the stock's canonical unit (metre or kilogram). The difference
 * and the tolerance status are judged against the customer's original request. Never a reservation:
 * applying an option is a separate command.
 *
 * @param optionKey stable within one evaluation; the same pieces give the same key
 * @param placement the main proposal, an option below or above the request, or the salesperson's
 *     own pick
 * @param kind the quantity relative to the request, kept for the acceptance flow
 * @param source whether only free stock is used, or stock other quotes hold
 * @param reasons why the option ranks as it does, in the order of the tenant-first criteria
 * @param heldStock what other quotes hold that this option would take; empty for free options
 * @param blockedReason why the option cannot be applied; null when it can
 */
public record QuantityOption(
    String optionKey,
    OptionPlacement placement,
    OptionKind kind,
    Compatibility compatibility,
    StockSource source,
    BigDecimal quantity,
    BigDecimal canonicalQuantity,
    BigDecimal difference,
    BigDecimal differencePercent,
    ToleranceStatus toleranceStatus,
    List<ProposalReason> reasons,
    List<LotPart> lots,
    List<HeldStock> heldStock,
    BlockedReason blockedReason) {

  public QuantityOption {
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
    lots = List.copyOf(lots);
    heldStock = heldStock == null ? List.of() : List.copyOf(heldStock);
  }

  public boolean applicable() {
    return blockedReason == null;
  }

  public boolean outsideAgreedTolerance() {
    return toleranceStatus == ToleranceStatus.OUTSIDE;
  }

  public int lotCount() {
    return lots.size();
  }

  /**
   * A used lot keeps one or two pieces (Fatih, 2026-10-13). A computed option never does; only a
   * salesperson's own pick can, and then only with acknowledgement.
   */
  public boolean leavesRemnant() {
    return lots.stream().anyMatch(LotPart::remnant);
  }

  /** The remnant warning must be acknowledged before the option is applied. */
  public boolean needsRemnantAcknowledgement() {
    return leavesRemnant();
  }

  public List<UUID> pieceIds() {
    return lots.stream().flatMap(lot -> lot.pieceIds().stream()).toList();
  }

  public List<UUID> batchIds() {
    return lots.stream().map(LotPart::batchId).toList();
  }

  /** The same option with its ranking reasons. */
  public QuantityOption withReasons(List<ProposalReason> newReasons) {
    return new QuantityOption(
        optionKey,
        placement,
        kind,
        compatibility,
        source,
        quantity,
        canonicalQuantity,
        difference,
        differencePercent,
        toleranceStatus,
        newReasons,
        lots,
        heldStock,
        blockedReason);
  }

  /** The same option in another placement. */
  public QuantityOption as(OptionPlacement newPlacement) {
    return new QuantityOption(
        optionKey,
        newPlacement,
        kind,
        compatibility,
        source,
        quantity,
        canonicalQuantity,
        difference,
        differencePercent,
        toleranceStatus,
        reasons,
        lots,
        heldStock,
        blockedReason);
  }

  /** What the option is to the salesperson. */
  @Schema(name = "StockProposalPlacement", enumAsRef = true)
  public enum OptionPlacement {
    /**
     * The option the proposal opens at: the best that meets the request within the tolerance,
     * otherwise the nearest above it, otherwise the best below it, otherwise held stock.
     */
    MAIN,
    /** A computed alternative below the request. */
    BELOW,
    /** A computed alternative at or above the request. */
    ABOVE,
    /** Pieces the salesperson picked, evaluated with the same checks. */
    MANUAL
  }

  /** Where the option's stock comes from. */
  @Schema(name = "StockProposalSource", enumAsRef = true)
  public enum StockSource {
    FREE,
    /** Stock others hold; offered only when free stock cannot reach the lower tolerance limit. */
    HELD
  }

  @Schema(name = "StockProposalToleranceStatus", enumAsRef = true)
  public enum ToleranceStatus {
    WITHIN,
    OUTSIDE
  }

  /** Why an option cannot be applied as it stands. */
  @Schema(name = "StockProposalBlockedReason", enumAsRef = true)
  public enum BlockedReason {
    /** Several lots without technical confirmation or a concrete tone acceptance (SOI S05). */
    COMPATIBILITY_CONFIRMATION_REQUIRED,
    /** The customer requires a single lot; only a manual pick can break it. */
    SINGLE_LOT_REQUIRED,
    /** Another quote holds pieces or quantity the option takes; it must be released first. */
    HELD_BY_ANOTHER_QUOTE,
    /** A hold record whose quote intent cannot be verified covers pieces the option takes. */
    HOLD_UNVERIFIED
  }

  /** What the option is relative to the request. */
  @Schema(name = "QuantityOptionKind", enumAsRef = true)
  public enum OptionKind {
    EXACT,
    ABOVE,
    BELOW,
    /** The requested quantity, leaving one or two pieces in a used lot (a manual pick only). */
    REQUESTED_WITH_REMNANT
  }

  /** Why several lots may (or may not yet) ship together (SOI A02, A04, A04-b). */
  @Schema(name = "QuantityOptionCompatibility", enumAsRef = true)
  public enum Compatibility {
    SINGLE_LOT,
    CONFIRMED,
    TONE_ACCEPTED,
    /** Not a definite option until technical confirmation or a concrete tone acceptance. */
    PENDING_CONFIRMATION
  }

  /**
   * The part of one lot an option takes.
   *
   * @param remainingEligiblePieces eligible pieces of the lot the option leaves, held or free
   * @param remainingQuantity their quantity in the line unit
   * @param unknownPieces pieces of the lot whose suitability is unknown; never counted as pieces
   *     left, as they are not offered piece by piece (Fatih, 2026-10-13)
   * @param remnant one or two pieces of the pool the option was chosen from stay behind
   * @param remnantPieceIds those pieces
   * @param remnantQuantity their quantity in the line unit
   * @param closesLot the option takes every piece of the lot, so the lot is closed; never claimed
   *     while held, unknown or otherwise unsuitable pieces stay
   */
  public record LotPart(
      UUID batchId,
      String lotNo,
      Instant productionDate,
      List<UUID> pieceIds,
      BigDecimal quantity,
      int remainingEligiblePieces,
      BigDecimal remainingQuantity,
      int unknownPieces,
      boolean remnant,
      List<UUID> remnantPieceIds,
      BigDecimal remnantQuantity,
      boolean closesLot) {

    public LotPart {
      pieceIds = List.copyOf(pieceIds);
      remnantPieceIds = remnantPieceIds == null ? List.of() : List.copyOf(remnantPieceIds);
    }

    /** The lot is emptied of eligible pieces and holds no piece of unknown state. */
    public boolean exhausted() {
      return remainingEligiblePieces == 0 && unknownPieces == 0;
    }
  }

  /** What kind of hold an option would take from another quote. */
  @Schema(name = "StockProposalHeldStockKind", enumAsRef = true)
  public enum HeldStockKind {
    /** Named pieces a quote line holds. */
    PIECE_HOLD,
    /** A quantity a quote intends to take from the lot, not tied to pieces. */
    LOT_INTENT
  }

  /**
   * Stock another quote holds that an option would take. Quote, marketer and expiry are null when
   * the hold is not verified (no live quote intent backs it).
   *
   * @param quantity the intent's quantity in its own unit; null for a piece hold
   */
  public record HeldStock(
      UUID batchId,
      String lotNo,
      HeldStockKind kind,
      boolean verified,
      UUID quoteId,
      String quoteNumber,
      String marketerName,
      LocalDate expiresAt,
      List<UUID> pieceIds,
      BigDecimal quantity,
      String unit) {

    public HeldStock {
      pieceIds = pieceIds == null ? List.of() : List.copyOf(pieceIds);
    }
  }
}
