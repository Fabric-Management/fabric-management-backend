package com.fabricmanagement.production.core.stockunit.dto;

import com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort.TargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StockUnitCutSessionDtos {
  private StockUnitCutSessionDtos() {}

  @Schema(name = "StockUnitCutSessionState", enumAsRef = true)
  public enum State {
    OPEN,
    HELD,
    EXPIRED,
    ENDED
  }

  @Schema(name = "StockUnitCutSourceAvailability", enumAsRef = true)
  public enum Availability {
    AVAILABLE,
    HELD,
    UNAVAILABLE
  }

  @Schema(name = "StockUnitCutSelectionReason", enumAsRef = true)
  public enum Reason {
    SELECT_SOURCES,
    INSUFFICIENT_SOURCES,
    WHOLE_ROLLS_MATCH,
    CUT_SOURCE_REQUIRED,
    REFRESH_REQUIRED,
    OWNERSHIP_REQUIRED,
    SELECTION_ENDED
  }

  @Schema(name = "OpenStockUnitCutSessionRequest")
  public record Open(
      @NotNull TargetType targetType, @NotNull UUID documentId, @NotNull UUID lineId) {}

  @Schema(name = "ReplaceStockUnitCutSourcesRequest")
  public record Pick(
      @NotNull @PositiveOrZero Long expectedRevision,
      @NotEmpty @Size(max = 500) List<@NotNull UUID> stockUnitIds,
      @Schema(
              nullable = true,
              description =
                  "Optional explicit source; otherwise the server chooses a valid cut source")
          UUID cutSourceId) {}

  @Schema(name = "StockUnitCutSessionTokensRequest")
  public record Tokens(
      @NotNull @PositiveOrZero Long expectedRevision,
      @NotNull @Size(max = 500) List<@NotNull UUID> tokens) {}

  @Schema(name = "ApplyStockUnitCutSessionRequest")
  public record Apply(
      @NotNull UUID operationId,
      @NotNull @PositiveOrZero Long expectedRevision,
      @NotNull @PositiveOrZero Long expectedDocumentVersion,
      @NotEmpty @Size(max = 500) List<@NotNull UUID> tokens) {}

  @Schema(name = "StockUnitCutLeasePolicy")
  public record Policy(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long leaseSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long renewAfterSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long idleAfterSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long idleWarningSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int maxKeysPerRequest) {}

  @Schema(name = "StockUnitCutLeaseGrant")
  public record Grant(@NotNull UUID stockUnitId, @NotNull UUID token, @NotNull Instant expiresAt) {}

  @Schema(name = "StockUnitCutSourceCandidate")
  public record Candidate(
      @NotNull UUID stockUnitId,
      @NotNull UUID batchId,
      @Schema(nullable = true) String serialNumber,
      @NotNull BigDecimal length,
      @NotNull String unit,
      @NotNull Availability availability,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          BigDecimal freeLotQuantity) {}

  @Schema(name = "StockUnitCutSession")
  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
  public record View(
      @NotNull UUID selectionId,
      @NotNull UUID sessionId,
      @NotNull Long revision,
      @NotNull Long documentVersion,
      @NotNull State state,
      @NotNull Policy policy,
      @NotNull List<Grant> grants,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long remainingMs,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant expiresAt,
      @NotNull List<UUID> sourceIds,
      @NotNull List<Candidate> candidates,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Reason evaluationReason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          StockUnitCutPlanDtos.View evaluation) {}
}
