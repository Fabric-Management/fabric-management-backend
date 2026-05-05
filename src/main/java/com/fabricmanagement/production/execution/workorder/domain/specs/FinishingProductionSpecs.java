package com.fabricmanagement.production.execution.workorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Production specifications for finishing/apre. */
public record FinishingProductionSpecs(
    @Schema(description = "Type of finishing", example = "Enzyme Wash") String finishType,
    @Schema(description = "Stenter/Ram temperature in °C", example = "160.0")
        Double stenterTemperature,
    @Schema(description = "Stenter speed in m/min", example = "30.0") Double stenterSpeed,
    @Schema(description = "Overfeed percentage", example = "3.0") Double overfeedPercent,
    @Schema(description = "Target fabric width in cm", example = "155.0") Double targetWidth,
    @Schema(description = "Target fabric weight in GSM", example = "180") Integer targetGsm,
    @Schema(description = "Compacting pressure in bar", example = "4.5") Double compactingPressure,
    @Schema(description = "Type of softener applied", example = "Silicone") String softenerType,
    @Schema(description = "Target shrinkage percentage", example = "-3.0") Double shrinkageTarget,
    @Schema(
            description = "Chemical applications applied",
            example = "[\"Anti-pilling\", \"Water repellent\"]")
        List<String> chemicalApplications)
    implements WorkOrderProductionSpecs {
  public FinishingProductionSpecs {
    chemicalApplications =
        chemicalApplications != null ? List.copyOf(chemicalApplications) : List.of();
  }
}
