package com.fabricmanagement.production.execution.workorder.app.validation;

import static com.fabricmanagement.production.execution.workorder.app.validation.ProductionValidationConstants.*;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.specs.KnittingProductionSpecs;
import com.fabricmanagement.production.execution.workorder.domain.specs.WorkOrderProductionSpecs;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnittingProductionValidator implements WorkOrderProductionValidator {

  @Override
  public WorkOrderModuleType getSupportedType() {
    return WorkOrderModuleType.KNITTING;
  }

  @Override
  public List<String> validateOnCreate(WorkOrderProductionSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof KnittingProductionSpecs kn) {
      if (kn.machineDiameter() != null && kn.machineDiameter() <= 0) {
        violations.add("Machine diameter must be greater than zero");
      }
      if (kn.machineSpeed() != null && kn.machineSpeed() <= 0) {
        violations.add("Machine speed must be greater than zero");
      }
      if (kn.needleCount() != null && kn.needleCount() <= 0) {
        violations.add("Needle count must be greater than zero");
      }
      if (kn.machineGauge() != null && kn.machineGauge() <= 0) {
        violations.add("Machine gauge must be greater than zero");
      }
      if (kn.stitchLength() != null && kn.stitchLength() <= 0) {
        violations.add("Stitch length must be greater than zero");
      }
      if (kn.targetGsm() != null && (kn.targetGsm() < GSM_MIN || kn.targetGsm() > GSM_MAX)) {
        violations.add("Target GSM must be between " + GSM_MIN + " and " + GSM_MAX);
      }
      if (kn.targetWidth() != null
          && (kn.targetWidth() < TUBE_WIDTH_MIN || kn.targetWidth() > TUBE_WIDTH_MAX)) {
        violations.add(
            "Target tube width must be between " + TUBE_WIDTH_MIN + " and " + TUBE_WIDTH_MAX);
      }
    }
    return violations;
  }

  @Override
  public List<String> validateOnStart(WorkOrderProductionSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof KnittingProductionSpecs kn) {
      if (kn.machineType() == null || kn.machineType().isBlank()) {
        violations.add("Machine type is required to start production");
      }
      if (kn.knitPattern() == null || kn.knitPattern().isBlank()) {
        violations.add("Knit pattern is required to start production");
      }
      if (kn.machineGauge() == null) {
        violations.add("Machine gauge is required to start production");
      }
    }
    return violations;
  }
}
