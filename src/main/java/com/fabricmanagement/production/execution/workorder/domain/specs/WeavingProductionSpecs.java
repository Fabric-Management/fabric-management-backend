package com.fabricmanagement.production.execution.workorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

/** Production specifications for weaving (Yarn → Woven Fabric). */
public record WeavingProductionSpecs(
    @Schema(description = "Loom type (e.g., Air-jet, Rapier, Projectile)", example = "Air-jet")
        String loomType,
    @Schema(description = "Loom speed in RPM", example = "600") Integer loomSpeed,
    @Schema(description = "Warp density in threads per cm", example = "28.0") Double warpDensity,
    @Schema(description = "Weft density in threads per cm", example = "22.0") Double weftDensity,
    @Schema(description = "Reed number", example = "72") String reedNumber,
    @Schema(description = "Weave pattern (e.g., Plain, Twill, Satin)", example = "2/1 Twill")
        String weavePattern,
    @Schema(description = "Warp tension in cN", example = "150.0") Double warpTension,
    @Schema(description = "Target fabric width in cm", example = "160.0") Double targetWidth,
    @Schema(description = "Target fabric weight in GSM", example = "120") Integer targetGsm,
    @Schema(description = "Warp yarn information", example = "Ne 40/1 Cotton") String warpYarnInfo,
    @Schema(description = "Weft yarn information", example = "Ne 30/1 Cotton") String weftYarnInfo)
    implements WorkOrderProductionSpecs {}
