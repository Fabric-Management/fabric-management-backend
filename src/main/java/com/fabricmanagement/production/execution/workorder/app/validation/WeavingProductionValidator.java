package com.fabricmanagement.production.execution.workorder.app.validation;

import static com.fabricmanagement.production.execution.workorder.app.validation.ProductionValidationConstants.*;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.specs.WeavingProductionSpecs;
import com.fabricmanagement.production.execution.workorder.domain.specs.WorkOrderProductionSpecs;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WeavingProductionValidator implements WorkOrderProductionValidator {

  @Override
  public WorkOrderModuleType getSupportedType() {
    return WorkOrderModuleType.WEAVING;
  }

  @Override
  public List<String> validateOnCreate(WorkOrderProductionSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof WeavingProductionSpecs wv) {
      if (wv.loomSpeed() != null && wv.loomSpeed() <= 0) {
        violations.add("Loom speed must be greater than zero");
      }
      if (wv.warpDensity() != null && wv.warpDensity() <= 0) {
        violations.add("Warp density must be greater than zero");
      }
      if (wv.weftDensity() != null && wv.weftDensity() <= 0) {
        violations.add("Weft density must be greater than zero");
      }
      if (wv.warpTension() != null && wv.warpTension() <= 0) {
        violations.add("Warp tension must be greater than zero");
      }
      if (wv.targetGsm() != null && (wv.targetGsm() < GSM_MIN || wv.targetGsm() > GSM_MAX)) {
        violations.add("Target GSM must be between " + GSM_MIN + " and " + GSM_MAX);
      }
      if (wv.targetWidth() != null
          && (wv.targetWidth() < WIDTH_MIN || wv.targetWidth() > WIDTH_MAX)) {
        violations.add("Target width must be between " + WIDTH_MIN + " and " + WIDTH_MAX);
      }
    }
    return violations;
  }

  @Override
  public List<String> validateOnStart(WorkOrderProductionSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof WeavingProductionSpecs wv) {
      if (wv.loomType() == null || wv.loomType().isBlank()) {
        violations.add("Loom type is required to start production");
      }
      if (wv.weavePattern() == null || wv.weavePattern().isBlank()) {
        violations.add("Weave pattern is required to start production");
      }
      if (wv.warpYarnInfo() == null || wv.warpYarnInfo().isBlank()) {
        violations.add("Warp yarn info is required to start production");
      }
      if (wv.weftYarnInfo() == null || wv.weftYarnInfo().isBlank()) {
        violations.add("Weft yarn info is required to start production");
      }
    }
    return violations;
  }
}
