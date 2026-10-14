package com.fabricmanagement.platform.tenant.dto;

import com.fabricmanagement.platform.tenant.domain.StockProposalSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * A tenant's stock-proposal settings as stored, defaults filled in (Fatih, 2026-10-14). Named apart
 * from the domain record, which reaches the API through the tenant's settings.
 */
@Schema(name = "StockProposalSettingsView")
public record StockProposalSettingsDto(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Lower tolerance a new order line starts with, in percent")
        BigDecimal toleranceDownPct,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Upper tolerance a new order line starts with, in percent")
        BigDecimal toleranceUpPct,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Normal pieces a lot keeps while its product sells as before")
        int minHealthyPiecesMoving,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Normal pieces a lot keeps while its product sells less")
        int minHealthyPiecesSlowing,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Normal pieces a lot keeps once its product hardly sells")
        int minHealthyPiecesStalled,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "A piece under this percent of its lot's average piece is short")
        BigDecimal shortPiecePct,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Days whose shipments are compared with the days before them")
        int speedWindowDays,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Shipping less than this percent of the period before: slowing")
        BigDecimal slowingBelowPct,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Shipping less than this percent of the period before: stalled")
        BigDecimal stalledBelowPct,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Free stock lasting longer than this many days: slowing")
        int slowingCoverDays,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "How far, in percent, a lot-closing option may exceed the upper tolerance for a"
                    + " slowing product")
        BigDecimal lotCloseExtraSlowingPct,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The same for a stalled product")
        BigDecimal lotCloseExtraStalledPct) {

  public static StockProposalSettingsDto from(StockProposalSettings settings) {
    return new StockProposalSettingsDto(
        settings.toleranceDownPct(),
        settings.toleranceUpPct(),
        settings.minHealthyPiecesMoving(),
        settings.minHealthyPiecesSlowing(),
        settings.minHealthyPiecesStalled(),
        settings.shortPiecePct(),
        settings.speedWindowDays(),
        settings.slowingBelowPct(),
        settings.stalledBelowPct(),
        settings.slowingCoverDays(),
        settings.lotCloseExtraSlowingPct(),
        settings.lotCloseExtraStalledPct());
  }
}
