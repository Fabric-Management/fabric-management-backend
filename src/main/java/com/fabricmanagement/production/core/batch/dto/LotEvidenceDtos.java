package com.fabricmanagement.production.core.batch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lot evidence contracts for order intake (SOI IK-08, A04). */
public final class LotEvidenceDtos {

  private LotEvidenceDtos() {}

  @Schema(name = "RecordLotFinishedWidthRequest")
  public record RecordFinishedWidthRequest(
      @NotNull @DecimalMin("0.01") BigDecimal value,
      @NotNull @Pattern(regexp = "CM|IN|cm|in") String unit,
      @Size(max = 500) String methodNote) {}

  @Schema(name = "LotFinishedWidthMeasurement")
  public record FinishedWidthMeasurementDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID batchId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal value,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unit,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String methodNote,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID measuredBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant measuredAt) {}

  @Schema(name = "ConfirmLotCompatibilityRequest")
  public record ConfirmCompatibilityRequest(
      @NotNull @Size(min = 2, max = 20) List<@NotNull UUID> batchIds,
      @Schema(description = "Limit the confirmation to one customer's orders") UUID customerId,
      @NotBlank @Size(max = 2000) String conditions) {}

  @Schema(name = "LotCompatibilityRequest")
  public record CompatibilityRequestDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> batchIds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID customerId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceType,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID sourceId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String note,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID requestedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant requestedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          com.fabricmanagement.production.core.batch.domain.LotCompatibilityRequestStatus status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant resolvedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          String resolutionNote) {}

  @Schema(name = "DeclineLotCompatibilityRequest")
  public record DeclineCompatibilityRequest(@NotBlank @Size(max = 2000) String reason) {}

  @Schema(name = "LotCompatibilityConfirmation")
  public record CompatibilityConfirmationDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> batchIds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID customerId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String conditions,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID confirmedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant confirmedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant revokedAt) {}
}
