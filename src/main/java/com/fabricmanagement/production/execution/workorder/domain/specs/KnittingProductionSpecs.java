package com.fabricmanagement.production.execution.workorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

/** Production specifications for knitting (Yarn → Knitted Fabric). */
public record KnittingProductionSpecs(
    @Schema(
            description = "Machine type (e.g., Single Jersey, Rib, Interlock)",
            example = "Single Jersey")
        String machineType,
    @Schema(description = "Machine diameter in inches", example = "30") Integer machineDiameter,
    @Schema(description = "Machine gauge (E)", example = "28") Integer machineGauge,
    @Schema(description = "Total number of needles", example = "2640") Integer needleCount,
    @Schema(description = "Machine speed in RPM", example = "26") Integer machineSpeed,
    @Schema(description = "Stitch length in mm", example = "2.75") Double stitchLength,
    @Schema(description = "Yarn tension in cN", example = "4.5") Double yarnTension,
    @Schema(description = "Knitting pattern", example = "Single Jersey") String knitPattern,
    @Schema(description = "Number of feeders", example = "96") Integer feederCount,
    @Schema(description = "Target fabric weight in GSM", example = "180") Integer targetGsm,
    @Schema(description = "Target tube width in cm", example = "90.0") Double targetWidth)
    implements WorkOrderProductionSpecs {}
