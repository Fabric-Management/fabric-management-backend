package com.fabricmanagement.production.execution.workorder.app.validation;

import static com.fabricmanagement.production.execution.workorder.app.validation.ProductionValidationConstants.MOISTURE_MAX;
import static com.fabricmanagement.production.execution.workorder.app.validation.ProductionValidationConstants.MOISTURE_MIN;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.specs.SpinningProductionSpecs;
import com.fabricmanagement.production.execution.workorder.domain.specs.WorkOrderProductionSpecs;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SpinningProductionValidator implements WorkOrderProductionValidator {

  @Override
  public WorkOrderModuleType getSupportedType() {
    return WorkOrderModuleType.SPINNING;
  }

  @Override
  public List<String> validateOnCreate(WorkOrderProductionSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof SpinningProductionSpecs sp) {
      if (sp.spindleSpeed() != null && sp.spindleSpeed() <= 0) {
        violations.add("Spindle speed must be greater than zero");
      }
      if (sp.twistPerInch() != null && sp.twistPerInch() <= 0) {
        violations.add("Twist per inch must be greater than zero");
      }
      if (sp.twistDirection() != null
          && !sp.twistDirection().equals("S")
          && !sp.twistDirection().equals("Z")) {
        violations.add("Twist direction must be 'S' or 'Z'");
      }
      if (sp.targetMoisturePercent() != null
          && (sp.targetMoisturePercent() < MOISTURE_MIN
              || sp.targetMoisturePercent() > MOISTURE_MAX)) {
        violations.add(
            "Target moisture percent must be between " + MOISTURE_MIN + " and " + MOISTURE_MAX);
      }
      if (sp.draftRatio() != null && sp.draftRatio() <= 0) {
        violations.add("Draft ratio must be greater than zero");
      }
    }
    return violations;
  }

  @Override
  public List<String> validateOnStart(WorkOrderProductionSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof SpinningProductionSpecs sp) {
      if (sp.targetYarnCount() == null || sp.targetYarnCount().isBlank()) {
        violations.add("Target yarn count is required to start production");
      }
      if (sp.spinningMethod() == null || sp.spinningMethod().isBlank()) {
        violations.add("Spinning method is required to start production");
      }
      if (sp.twistDirection() == null || sp.twistDirection().isBlank()) {
        violations.add("Twist direction is required to start production");
      }
    }
    return violations;
  }
}
