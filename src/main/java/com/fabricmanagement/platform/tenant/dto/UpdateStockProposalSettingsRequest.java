package com.fabricmanagement.platform.tenant.dto;

import com.fabricmanagement.platform.tenant.domain.StockProposalSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Every stock-proposal setting at once (Fatih, 2026-10-14). Only administrators send it: users who
 * may manage members.
 */
@Schema(name = "UpdateStockProposalSettingsRequest")
public record UpdateStockProposalSettingsRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
        @NotNull
        @DecimalMin(value = "0", message = "A tolerance cannot be negative")
        @DecimalMax(value = "100", message = "A tolerance cannot exceed 100 percent")
        BigDecimal toleranceDownPct,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
        @NotNull
        @DecimalMin(value = "0", message = "A tolerance cannot be negative")
        @DecimalMax(value = "100", message = "A tolerance cannot exceed 100 percent")
        BigDecimal toleranceUpPct,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") @NotNull @Min(1) @Max(50)
        Integer minHealthyPiecesMoving,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5") @NotNull @Min(1) @Max(50)
        Integer minHealthyPiecesSlowing,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "8") @NotNull @Min(1) @Max(50)
        Integer minHealthyPiecesStalled,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "51")
        @NotNull
        @DecimalMin(value = "1")
        @DecimalMax(value = "99")
        BigDecimal shortPiecePct,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "90") @NotNull @Min(30) @Max(365)
        Integer speedWindowDays,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "80")
        @NotNull
        @DecimalMin(value = "1")
        @DecimalMax(value = "100")
        BigDecimal slowingBelowPct,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "40")
        @NotNull
        @DecimalMin(value = "0")
        @DecimalMax(value = "99")
        BigDecimal stalledBelowPct,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "180")
        @NotNull
        @Min(1)
        @Max(3650)
        Integer slowingCoverDays,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
        @NotNull
        @DecimalMin(value = "0")
        @DecimalMax(value = "100")
        BigDecimal lotCloseExtraSlowingPct,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
        @NotNull
        @DecimalMin(value = "0")
        @DecimalMax(value = "100")
        BigDecimal lotCloseExtraStalledPct) {

  @AssertTrue(message = "Minimum pieces rise with slower sales: moving <= slowing <= stalled")
  @Schema(hidden = true)
  public boolean isMinimumPiecesOrdered() {
    if (minHealthyPiecesMoving == null
        || minHealthyPiecesSlowing == null
        || minHealthyPiecesStalled == null) {
      return true;
    }
    return minHealthyPiecesMoving <= minHealthyPiecesSlowing
        && minHealthyPiecesSlowing <= minHealthyPiecesStalled;
  }

  @AssertTrue(message = "The stalled limit lies below the slowing limit")
  @Schema(hidden = true)
  public boolean isSpeedLimitsOrdered() {
    if (slowingBelowPct == null || stalledBelowPct == null) {
      return true;
    }
    return stalledBelowPct.compareTo(slowingBelowPct) < 0;
  }

  @AssertTrue(
      message = "A stalled product may exceed the tolerance at least as far as a slowing one")
  @Schema(hidden = true)
  public boolean isLotCloseExtrasOrdered() {
    if (lotCloseExtraSlowingPct == null || lotCloseExtraStalledPct == null) {
      return true;
    }
    return lotCloseExtraSlowingPct.compareTo(lotCloseExtraStalledPct) <= 0;
  }

  public StockProposalSettings toSettings() {
    return new StockProposalSettings(
        toleranceDownPct,
        toleranceUpPct,
        minHealthyPiecesMoving,
        minHealthyPiecesSlowing,
        minHealthyPiecesStalled,
        shortPiecePct,
        speedWindowDays,
        slowingBelowPct,
        stalledBelowPct,
        slowingCoverDays,
        lotCloseExtraSlowingPct,
        lotCloseExtraStalledPct);
  }
}
