package com.fabricmanagement.production.execution.workorder.app.validation;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.specs.WorkOrderProductionSpecs;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GenericProductionValidator implements WorkOrderProductionValidator {

  @Override
  public WorkOrderModuleType getSupportedType() {
    return WorkOrderModuleType.GENERIC;
  }

  @Override
  public List<String> validateOnCreate(WorkOrderProductionSpecs specs) {
    return List.of();
  }

  @Override
  public List<String> validateOnStart(WorkOrderProductionSpecs specs) {
    return List.of();
  }
}
