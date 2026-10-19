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
      UUID targetLineId) {
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
                  "Salesperson acknowledgement of backend tolerance/remnant facts; never customer consent")
          boolean confirmed) {}
}
