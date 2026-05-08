package com.fabricmanagement.production.execution.workorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Production specifications for dyeing. */
public record DyeingProductionSpecs(
    @Schema(description = "Dyeing method (e.g., Jet, Pad, Beam, Hank)", example = "Jet")
        String dyeMethod,
    @Schema(description = "Dye recipe reference", example = "DR-2026-0042") String dyeRecipeRef,
    @Schema(description = "Target color (Pantone or internal code)", example = "Pantone 7621C")
        String targetColor,
    @Schema(description = "Lab dip reference", example = "LD-2026-019") String labDipRef,
    @Schema(description = "Liquor/Bath ratio", example = "1:8") String bathRatio,
    @Schema(description = "Temperature profile", example = "60°C/30min → 98°C/45min")
        String temperatureProfile,
    @Schema(description = "Target pH level", example = "4.5") Double phTarget,
    @Schema(description = "Machine type", example = "Overflow Jet") String machineType,
    @Schema(description = "CIELab L* lightness target", example = "45.0") Double lightnessTarget,
    @Schema(
            description = "Fastness targets (e.g., Wash 4-5, Rub 4)",
            example = "[\"Wash 4-5\", \"Rub 4\"]")
        List<String> fastnessTargets)
    implements WorkOrderProductionSpecs {
  public DyeingProductionSpecs {
    fastnessTargets = fastnessTargets != null ? List.copyOf(fastnessTargets) : List.of();
  }
}
