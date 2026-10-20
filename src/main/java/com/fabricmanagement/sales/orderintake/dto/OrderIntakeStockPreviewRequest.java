package com.fabricmanagement.sales.orderintake.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * An order line that is not saved yet, as the order form holds it (STOCK-PREVIEW-1). The
 * constraints and messages are those of a saved line ({@code SalesOrderLineRequest}), so a preview
 * accepts what a line accepts. Tolerances are on the percent scale the line stores (5 means 5%);
 * both are required and 0 is an explicit value (Fatih, 2026-10-13). {@code manualPieceIds} is the
 * salesperson's own pick ("change pieces"), evaluated as a MANUAL option with the same checks
 * (STOCK-PROPOSAL-2).
 */
@Schema(name = "OrderIntakeStockPreviewRequest")
public record OrderIntakeStockPreviewRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "Product is required")
        UUID productId,
    @Schema(description = "Colour card; omit for colourless stock") UUID colorId,
    @Schema(description = "Finished width; must be one of the product's defined widths")
        @DecimalMin(value = "0.01", message = "Finished width must be positive")
        BigDecimal finishedWidth,
    @Schema(
            description = "Unit of the finished width",
            allowableValues = {"CM", "IN"})
        @Pattern(regexp = "CM|IN|cm|in", message = "Width unit must be CM or IN")
        String finishedWidthUnit,
    @Schema(description = "Customer, so its confirmed lot compatibility is honoured")
        UUID customerId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Requested quantity is required")
        @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
        BigDecimal requestedQty,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "Unit is required")
        String unit,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The customer requires this line from a single dye lot")
        @NotNull(message = "Single-lot requirement is required")
        Boolean singleLotRequired,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Quantity tolerance above the requested quantity agreed for this line (%); 0 when"
                    + " no deviation is accepted")
        @NotNull(message = TOLERANCE_REQUIRED)
        @DecimalMin(value = "0", message = "A tolerance cannot be negative")
        @DecimalMax(value = "100", message = "A tolerance cannot exceed 100 percent")
        BigDecimal toleranceUpPct,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Quantity tolerance below the requested quantity agreed for this line (%); 0 when"
                    + " no deviation is accepted")
        @NotNull(message = TOLERANCE_REQUIRED)
        @DecimalMin(value = "0", message = "A tolerance cannot be negative")
        @DecimalMax(value = "100", message = "A tolerance cannot exceed 100 percent")
        BigDecimal toleranceDownPct,
    @Schema(
            description =
                "Pieces the salesperson picked; evaluated as a MANUAL option beside the computed"
                    + " ones. Pieces that are no longer offerable are refused (409)")
        @Size(max = 500, message = "At most 500 pieces can be picked")
        List<@NotNull(message = "A picked piece is required") UUID> manualPieceIds,
    @Schema(
            description =
                "Optional current ownership proof; excludes only this authenticated form line's own held rolls")
        @jakarta.validation.Valid
        DraftStockSelectionRequests.Proof ownStockSelection) {

  public OrderIntakeStockPreviewRequest(
      UUID productId,
      UUID colorId,
      BigDecimal finishedWidth,
      String finishedWidthUnit,
      UUID customerId,
      BigDecimal requestedQty,
      String unit,
      Boolean singleLotRequired,
      BigDecimal toleranceUpPct,
      BigDecimal toleranceDownPct,
      List<UUID> manualPieceIds) {
    this(
        productId,
        colorId,
        finishedWidth,
        finishedWidthUnit,
        customerId,
        requestedQty,
        unit,
        singleLotRequired,
        toleranceUpPct,
        toleranceDownPct,
        manualPieceIds,
        null);
  }

  /** The message of a missing tolerance side; the same as a saved line's. */
  public static final String TOLERANCE_REQUIRED =
      "Enter both the upper and the lower tolerance; write 0 when no deviation is accepted";
}
