package com.fabricmanagement.procurement.purchaseorder.app.validation;

import static com.fabricmanagement.procurement.purchaseorder.app.validation.TextileValidationConstants.*;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.FabricPurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Validates {@link FabricPurchaseSpecs} against textile domain rules.
 *
 * <p>Create phase: GSM 80-400, width 100-320 cm (AGENTS.md Textile Domain). Shrinkage between -15%
 * and +15%.
 *
 * <p>Confirm phase: construction, composition, and fabricType must be specified.
 */
@Component
public class FabricPurchaseOrderValidator implements PurchaseOrderValidator {

  @Override
  public PurchaseOrderModuleType getSupportedType() {
    return PurchaseOrderModuleType.FABRIC;
  }

  @Override
  public List<String> validateOnCreate(PurchaseOrderSpecs specs) {
    var violations = new ArrayList<String>();

    if (specs instanceof FabricPurchaseSpecs fabric) {
      if (fabric.gsm() != null && (fabric.gsm() < GSM_MIN || fabric.gsm() > GSM_MAX)) {
        violations.add(
            "GSM must be between " + GSM_MIN + " and " + GSM_MAX + ", got: " + fabric.gsm());
      }
      if (fabric.widthCm() != null
          && (fabric.widthCm() < WIDTH_CM_MIN || fabric.widthCm() > WIDTH_CM_MAX)) {
        violations.add(
            "Width must be between "
                + WIDTH_CM_MIN
                + " and "
                + WIDTH_CM_MAX
                + " cm, got: "
                + fabric.widthCm());
      }
      if (fabric.shrinkage() != null
          && (fabric.shrinkage() < SHRINKAGE_MIN || fabric.shrinkage() > SHRINKAGE_MAX)) {
        violations.add(
            "Shrinkage must be between "
                + SHRINKAGE_MIN
                + " and "
                + SHRINKAGE_MAX
                + "%, got: "
                + fabric.shrinkage());
      }
    }

    return violations;
  }

  @Override
  public List<String> validateOnConfirm(PurchaseOrderSpecs specs) {
    // GSM/width range is already validated in validateOnCreate (engine re-runs it).
    // Here we only check completeness.
    var violations = new ArrayList<String>();

    if (specs instanceof FabricPurchaseSpecs fabric) {
      if (fabric.construction() == null || fabric.construction().isBlank()) {
        violations.add("Fabric construction is required before sending to supplier");
      }
      if (fabric.composition() == null || fabric.composition().isBlank()) {
        violations.add("Composition is required before sending to supplier");
      }
      if (fabric.fabricType() == null) {
        violations.add("Fabric type is required before sending to supplier");
      }
    }

    return violations;
  }
}
