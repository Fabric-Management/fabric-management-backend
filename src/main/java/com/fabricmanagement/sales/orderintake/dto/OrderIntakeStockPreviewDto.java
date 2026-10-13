package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Current stock for an order line that is not saved yet (STOCK-PREVIEW-1, STOCK-PROPOSAL-2).
 * Guidance for the salesperson only: nothing is reserved or recorded and it never reaches the
 * customer. The evaluation is the one a saved line with the same inputs gets; every option says
 * whether it uses free stock, what other quotes hold that it would take and whether it can be
 * applied. Beside it, what other quotes hold is reported per lot.
 *
 * <p>Unknown stays unknown: an unknown piece is never free, a quantity that cannot be stated in a
 * unit is null rather than zero, and a free quantity that depends on an unconvertible quote intent
 * or on an unverifiable piece hold is null with its reasons, never a number.
 */
@Schema(name = "OrderIntakeStockPreview")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderIntakeStockPreviewDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The line's unit")
        String unit,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Canonical stock unit (M for fabric, KG for yarn and fibre); \"?\" when the"
                    + " product's measure is unknown")
        String canonicalUnit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Evaluation evaluation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Lot> lots,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Totals totals,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Quote intents whose quantity could not be stated in the canonical unit")
        int unconvertibleIntentCount) {

  /** Why a free quantity could not be determined. */
  @Schema(name = "OrderIntakeStockPreviewFreeIncompleteReason", enumAsRef = true)
  public enum FreeIncompleteReason {
    /** A quote intent on the lot is in a unit that cannot be converted to the canonical unit. */
    UNCONVERTIBLE_INTENT,
    /** A piece of the lot carries a hold record whose quote intent cannot be verified. */
    UNVERIFIED_HOLD
  }

  /**
   * Whether a piece hold is backed by its quote line's active intent on the same lot. An unverified
   * hold is not evidence that a quote holds the piece, and no quote is named for it.
   */
  @Schema(name = "OrderIntakeStockPreviewHoldStatus", enumAsRef = true)
  public enum HoldStatus {
    VERIFIED,
    UNVERIFIED
  }

  /** A quantity in the canonical unit and, when it converts exactly, in the line's unit. */
  @Schema(name = "OrderIntakeStockPreviewQuantity")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Quantity(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal canonical,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Null when the line's unit cannot be converted from the canonical one")
          BigDecimal inLineUnit) {}

  /** The evaluation a saved line with the same inputs gets (SOI D2), without its fingerprint. */
  @Schema(name = "OrderIntakeStockPreviewEvaluation")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Evaluation(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          QuantityEvaluationResult.EvaluationStatus status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal requestedQuantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unit,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String canonicalUnit,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Ascending quantity; same quantity in the tenant-first ranking, then a"
                      + " deterministic order. Keep this order")
          List<QuantityProposalDto.Option> options,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description =
                  "Index of the main proposal in options; it may sit anywhere in the list. Null"
                      + " when no option can be the main one")
          Integer mainOptionIndex,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "False when a search limit was reached: the options found are not claimed to"
                      + " be the nearest or the best")
          boolean searchComplete,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int eligiblePieces,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unknownPieces,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int excludedPieces,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Why the evaluation or some pieces are unknown, e.g. UNIT_NOT_CONVERTIBLE,"
                      + " MEASURE_UNKNOWN, QUANTITY_BELOW_RESOLUTION")
          List<String> unknownReasons,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Every lot with offerable stock has a known production date")
          boolean agedByProductionDate) {

    public static Evaluation from(QuantityEvaluationResult result, Set<UUID> readableQuotes) {
      return new Evaluation(
          result.status(),
          result.requestedQuantity(),
          result.unit(),
          result.canonicalUnit(),
          QuantityProposalDto.options(result, readableQuotes),
          result.mainOptionIndex(),
          result.searchComplete(),
          result.eligiblePieces(),
          result.unknownPieces(),
          result.excludedPieces(),
          result.unknownReasons(),
          result.agedByProductionDate());
    }
  }

  /** One lot of the line's stock with what quotes hold on it. */
  @Schema(name = "OrderIntakeStockPreviewLot")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Lot(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID batchId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lotNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int eligiblePieces,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unknownPieces,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Records with no exact measure: a record to complete, never offerable stock;"
                      + " counted, never summed")
          int unmeasuredPieces,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Pieces with a recorded quantity and confirmed saleability")
          Quantity eligible,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Measured pieces awaiting suitability evidence (quality, width, verification);"
                      + " never ready stock. Unmeasured records are counted in unmeasuredPieces,"
                      + " not here")
          Quantity unknown,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Sum of the convertible quote intents on the lot")
          Quantity heldByQuotes,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Eligible pieces with an unverified hold record; reported apart, never treated"
                      + " as a proven commitment")
          Quantity unverifiedHold,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unverifiedHoldPieces,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description =
                  "Eligible minus intent-held, never below zero; null when freeComplete is false")
          Quantity free,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean freeComplete,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          List<FreeIncompleteReason> freeIncompleteReasons,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Intent> intents,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<HeldPiece> heldPieces,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Offerable pieces (recorded measure, confirmed saleability), for a manual pick")
          List<Piece> pieces) {}

  /**
   * One offerable piece. {@code held}: a hold record exists on it (verified or not); a pick using
   * it is evaluated as held stock.
   */
  @Schema(name = "OrderIntakeStockPreviewPiece")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Piece(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID stockUnitId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String pieceNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Quantity quantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean held) {}

  /**
   * An active quote lot-quantity intent. The quote and marketer are named only when the viewer may
   * read that quote.
   */
  @Schema(name = "OrderIntakeStockPreviewIntent")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Intent(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String quoteNumber,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String marketerName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal quantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unit,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Null when the quantity cannot be converted to the canonical unit")
          BigDecimal canonicalQuantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate expiresAt) {}

  /**
   * A piece with an active quote hold. Quote, marketer and expiry are always null when the hold is
   * UNVERIFIED, and the quote and marketer are null when the viewer may not read the quote.
   */
  @Schema(name = "OrderIntakeStockPreviewHeldPiece")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record HeldPiece(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID stockUnitId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String pieceNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) HoldStatus holdStatus,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String quoteNumber,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String marketerName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDate expiresAt) {}

  /** The line's stock across lots. */
  @Schema(name = "OrderIntakeStockPreviewTotals")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Totals(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Null when any lot's free quantity, or the measure, is unknown")
          Quantity free,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Measured stock awaiting suitability evidence; never ready stock")
          Quantity unknown,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Quantity heldByQuotes,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Quantity unverifiedHold,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description =
                  "Requested minus ready free stock, never below zero; null when free is null or"
                      + " the line's unit cannot be converted. Stock awaiting suitability,"
                      + " unmeasured records and work in progress never reduce it. It is a"
                      + " quantity comparison, not physical absence, required production or"
                      + " overall order coverage")
          Quantity notCoveredByReadyStock,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean freeComplete,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          List<FreeIncompleteReason> freeIncompleteReasons) {}
}
