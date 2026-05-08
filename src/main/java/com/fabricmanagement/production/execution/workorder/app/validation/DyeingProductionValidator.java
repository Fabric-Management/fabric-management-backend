package com.fabricmanagement.production.execution.workorder.app.validation;

import static com.fabricmanagement.production.execution.workorder.app.validation.ProductionValidationConstants.*;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.specs.DyeingProductionSpecs;
import com.fabricmanagement.production.execution.workorder.domain.specs.WorkOrderProductionSpecs;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DyeingProductionValidator implements WorkOrderProductionValidator {

  @Override
  public WorkOrderModuleType getSupportedType() {
    return WorkOrderModuleType.DYEING;
  }

  @Override
  public List<String> validateOnCreate(WorkOrderProductionSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof DyeingProductionSpecs dy) {
      if (dy.phTarget() != null && (dy.phTarget() < PH_MIN || dy.phTarget() > PH_MAX)) {
        violations.add("pH target must be between " + PH_MIN + " and " + PH_MAX);
      }
      if (dy.lightnessTarget() != null
          && (dy.lightnessTarget() < 0 || dy.lightnessTarget() > 100)) {
        violations.add("Lightness target must be between 0 and 100");
      }
      if (dy.bathRatio() != null && !dy.bathRatio().matches("\\d+:\\d+")) {
        violations.add("Bath ratio must be in format 'X:Y' (e.g. '1:8')");
      }
      if (dy.fastnessTargets() != null && dy.fastnessTargets().isEmpty()) {
        violations.add("Fastness targets cannot be empty if provided");
      }
    }
    return violations;
  }

  @Override
  public List<String> validateOnStart(WorkOrderProductionSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof DyeingProductionSpecs dy) {
      if (dy.dyeMethod() == null || dy.dyeMethod().isBlank()) {
        violations.add("Dye method is required to start production");
      }
      if (dy.targetColor() == null || dy.targetColor().isBlank()) {
        violations.add("Target color is required to start production");
      }
      if (dy.bathRatio() == null || dy.bathRatio().isBlank()) {
        violations.add("Bath ratio is required to start production");
      }
      if (dy.temperatureProfile() == null || dy.temperatureProfile().isBlank()) {
        violations.add("Temperature profile is required to start production");
      }
    }
    return violations;
  }
}
