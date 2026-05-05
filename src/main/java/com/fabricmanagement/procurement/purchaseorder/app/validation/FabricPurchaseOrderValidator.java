package com.fabricmanagement.procurement.purchaseorder.app.validation;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.FabricPurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Validates {@link FabricPurchaseSpecs} against textile domain rules.
 *
 * <p>Create phase: GSM 80-400, width 100-320 cm (AGENTS.md Textile Domain).
 *
 * <p>Confirm phase: construction must be specified.
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
      if (fabric.gsm() != null && (fabric.gsm() < 80 || fabric.gsm() > 400)) {
        violations.add("GSM must be between 80 and 400, got: " + fabric.gsm());
      }
      if (fabric.widthCm() != null && (fabric.widthCm() < 100 || fabric.widthCm() > 320)) {
        violations.add("Width must be between 100 and 320 cm, got: " + fabric.widthCm());
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
    }

    return violations;
  }
}
