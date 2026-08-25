package com.fabricmanagement.production.core.workorder.domain.specs;

import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import io.swagger.v3.oas.annotations.media.Schema;

/** Generic production specifications for undefined or catch-all modules. */
public record GenericProductionSpecs(
    @Schema(description = "General production notes or process instructions") String processNotes)
    implements WorkOrderProductionSpecs {

  @Override
  public WorkOrderModuleType specType() {
    return WorkOrderModuleType.GENERIC;
  }
}
