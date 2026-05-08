package com.fabricmanagement.production.execution.workorder.app.validation;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.specs.WorkOrderProductionSpecs;
import java.util.List;

/** Strategy interface for validating module-specific production specifications. */
public interface WorkOrderProductionValidator {

  /** The module type this validator handles. */
  WorkOrderModuleType getSupportedType();

  /**
   * Validates specs during WorkOrder creation. Focuses on logical format and ranges (e.g., speed >
   * 0).
   *
   * @param specs The production specs to validate
   * @return A list of violation messages. Empty if valid.
   */
  List<String> validateOnCreate(WorkOrderProductionSpecs specs);

  /**
   * Validates specs before starting production. Focuses on completeness (mandatory fields).
   *
   * @param specs The production specs to validate
   * @return A list of violation messages. Empty if valid.
   */
  List<String> validateOnStart(WorkOrderProductionSpecs specs);
}
