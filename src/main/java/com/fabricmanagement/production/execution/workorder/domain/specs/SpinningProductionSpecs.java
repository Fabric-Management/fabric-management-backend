package com.fabricmanagement.production.execution.workorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

/** Production specifications for spinning (Fiber → Yarn). */
public record SpinningProductionSpecs(
    @Schema(description = "Target yarn count (e.g., '30/1')", example = "30/1")
        String targetYarnCount,
    @Schema(
            description = "Spinning method (e.g., Ring, Open-End, Vortex, Compact)",
            example = "Ring")
        String spinningMethod,
    @Schema(description = "Twist direction (S or Z)", example = "Z") String twistDirection,
    @Schema(description = "Twist per inch (TPI)", example = "22.5") Double twistPerInch,
    @Schema(description = "Spindle speed in RPM", example = "18000") Integer spindleSpeed,
    @Schema(description = "Traveler number", example = "5/0") String travelerNumber,
    @Schema(description = "Draft ratio", example = "35.0") Double draftRatio,
    @Schema(description = "Blend ratio (e.g., '60/40 Cotton/Poly')", example = "60/40 Cotton/Poly")
        String blendRatio,
    @Schema(description = "Target moisture content percentage", example = "7.5")
        Double targetMoisturePercent,
    @Schema(description = "Quality target (e.g., '25%' Uster)", example = "25%")
        String qualityTarget)
    implements WorkOrderProductionSpecs {}
