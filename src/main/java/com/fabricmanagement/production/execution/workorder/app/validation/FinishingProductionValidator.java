package com.fabricmanagement.production.execution.workorder.app.validation;

import static com.fabricmanagement.production.execution.workorder.app.validation.ProductionValidationConstants.*;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.specs.FinishingProductionSpecs;
import com.fabricmanagement.production.execution.workorder.domain.specs.WorkOrderProductionSpecs;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FinishingProductionValidator implements WorkOrderProductionValidator {

  @Override
  public WorkOrderModuleType getSupportedType() {
    return WorkOrderModuleType.FINISHING;
  }

  @Override
  public List<String> validateOnCreate(WorkOrderProductionSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof FinishingProductionSpecs fin) {
      if (fin.stenterTemperature() != null
          && (fin.stenterTemperature() < STENTER_TEMP_MIN
              || fin.stenterTemperature() > STENTER_TEMP_MAX)) {
        violations.add(
            "Stenter temperature must be between " + STENTER_TEMP_MIN + " and " + STENTER_TEMP_MAX);
      }
      if (fin.stenterSpeed() != null && fin.stenterSpeed() <= 0) {
        violations.add("Stenter speed must be greater than zero");
      }
      if (fin.overfeedPercent() != null
          && (fin.overfeedPercent() < OVERFEED_MIN || fin.overfeedPercent() > OVERFEED_MAX)) {
        violations.add("Overfeed percent must be between " + OVERFEED_MIN + " and " + OVERFEED_MAX);
      }
      if (fin.targetGsm() != null && (fin.targetGsm() < GSM_MIN || fin.targetGsm() > GSM_MAX)) {
        violations.add("Target GSM must be between " + GSM_MIN + " and " + GSM_MAX);
      }
      if (fin.targetWidth() != null
          && (fin.targetWidth() < WIDTH_MIN || fin.targetWidth() > WIDTH_MAX)) {
        violations.add("Target width must be between " + WIDTH_MIN + " and " + WIDTH_MAX);
      }
      if (fin.shrinkageTarget() != null
          && (fin.shrinkageTarget() < SHRINKAGE_MIN || fin.shrinkageTarget() > SHRINKAGE_MAX)) {
        violations.add(
            "Shrinkage target must be between " + SHRINKAGE_MIN + " and " + SHRINKAGE_MAX);
      }
    }
    return violations;
  }

  @Override
  public List<String> validateOnStart(WorkOrderProductionSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof FinishingProductionSpecs fin) {
      if (fin.finishType() == null || fin.finishType().isBlank()) {
        violations.add("Finish type is required to start production");
      }
      if (fin.targetGsm() == null) {
        violations.add("Target GSM is required to start production");
      }
    }
    return violations;
  }
}
