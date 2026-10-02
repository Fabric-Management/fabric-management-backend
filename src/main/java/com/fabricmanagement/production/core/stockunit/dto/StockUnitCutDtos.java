package com.fabricmanagement.production.core.stockunit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Piece cut contracts (SOI A06). Lengths are in the piece's own length unit. */
public final class StockUnitCutDtos {

  private StockUnitCutDtos() {}

  @Schema(name = "RecordStockUnitCutRequest")
  public record RecordCutRequest(
      @NotNull @DecimalMin("0.001") BigDecimal cutLength,
      @NotNull @DecimalMin("0.000") BigDecimal remainingLength) {}

  @Schema(name = "StockUnitCut")
  public record CutDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID stockUnitId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal cutLength,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal remainingLength,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lengthUnit,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID recordedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          UUID remainingVerifiedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          Instant remainingVerifiedAt) {}
}
