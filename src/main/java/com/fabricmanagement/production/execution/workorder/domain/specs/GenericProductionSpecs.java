package com.fabricmanagement.production.execution.workorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

/** Generic production specifications for undefined or catch-all modules. */
public record GenericProductionSpecs(
    @Schema(description = "General production notes or process instructions") String processNotes)
    implements WorkOrderProductionSpecs {}
