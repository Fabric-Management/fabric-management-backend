package com.fabricmanagement.sales.orderintake.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** Commands, never side effects of cached preview reads. */
public final class DraftStockSelectionRequests {
  private DraftStockSelectionRequests() {}

  @Schema(name = "OpenDraftStockSessionRequest")
  public record Open(
      @NotNull UUID formInstanceId,
      @NotNull UUID clientLineId,
      @NotNull UUID productId,
      UUID targetOrderId,
      UUID targetLineId,
      @Schema(description = "Editable quote target; mutually exclusive with targetOrderId")
          UUID targetQuoteId,
      @Schema(description = "Null for a new quote line; otherwise the saved quote line")
          UUID targetQuoteLineId) {
    public Open(UUID form, UUID line, UUID product, UUID order, UUID orderLine) {
      this(form, line, product, order, orderLine, null, null);
    }

    public Open(UUID form, UUID line, UUID product) {
      this(form, line, product, null, null);
    }
  }

  @Schema(name = "AcquireDraftStockSelectionRequest")
  public record Acquire(
      @NotNull UUID operationId,
      @NotNull @PositiveOrZero Long expectedRevision,
      @NotNull @Valid OrderIntakeStockPreviewRequest line,
      @NotBlank @Size(max = 40000) String optionKey,
      @NotNull @Size(max = 500) List<@NotNull UUID> previousTokens) {}

  @Schema(name = "DraftStockSelectionTokensRequest")
  public record Tokens(
      @NotNull @PositiveOrZero Long expectedRevision,
      @NotEmpty @Size(max = 500) List<@NotNull UUID> tokens) {}

  @Schema(name = "CloseDraftStockSessionRequest")
  public record Close(
      @NotNull @PositiveOrZero Long expectedRevision,
      @NotNull @Size(max = 500) List<@NotNull UUID> tokens) {}

  @Schema(name = "ApplyDraftStockSelectionRequest")
  public record Apply(
      @NotNull UUID operationId,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull @Valid Proof selection) {}

  @Schema(name = "RemoveAppliedStockSelectionRequest")
  public record Remove(@NotNull UUID operationId, @NotNull @PositiveOrZero Long expectedVersion) {}

  @Schema(name = "DraftStockSelectionProof")
  public record Proof(
      @NotNull UUID selectionId,
      @NotNull @PositiveOrZero Long selectionRevision,
      @NotEmpty @Size(max = 500) List<@NotNull UUID> tokens,
      @Schema(
              description =
                  "Salesperson acknowledgement of backend tolerance/remnant facts; never customer"
                      + " consent")
          boolean confirmed,
      @Schema(
              nullable = true,
              description =
                  "Explicit server-evaluated cut source; commercial quantity remains the exact requested length")
          UUID cutSourceId) {
    public Proof(UUID selection, Long revision, List<UUID> tokens, boolean confirmed) {
      this(selection, revision, tokens, confirmed, null);
    }
  }
}
