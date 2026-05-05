package com.fabricmanagement.procurement.purchaseorder.app.validation;

import static com.fabricmanagement.procurement.purchaseorder.app.validation.TextileValidationConstants.*;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.DyePurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Validates {@link DyePurchaseSpecs} for dye/finishing purchase orders.
 *
 * <p>Create phase: enforces lightness target length boundaries.
 *
 * <p>Confirm phase: colorCode, approvalDocumentId, and applicationMethod are mandatory. A dye PO
 * cannot be sent to a supplier without an approved lab-dip result.
 */
@Component
public class DyePurchaseOrderValidator implements PurchaseOrderValidator {

  @Override
  public PurchaseOrderModuleType getSupportedType() {
    return PurchaseOrderModuleType.DYE_FINISHING;
  }

  @Override
  public List<String> validateOnCreate(PurchaseOrderSpecs specs) {
    var violations = new ArrayList<String>();
    if (specs instanceof DyePurchaseSpecs dye) {
      if (dye.lightnessTarget() != null && dye.lightnessTarget().length() > LIGHTNESS_MAX_LENGTH) {
        violations.add("Lightness target must not exceed " + LIGHTNESS_MAX_LENGTH + " characters");
      }
    }
    return violations;
  }

  @Override
  public List<String> validateOnConfirm(PurchaseOrderSpecs specs) {
    var violations = new ArrayList<String>();

    if (specs instanceof DyePurchaseSpecs dye) {
      if (dye.colorCode() == null || dye.colorCode().isBlank()) {
        violations.add("Color code is required before sending dye order to supplier");
      }
      if (dye.labDipRef() == null || dye.labDipRef().isBlank()) {
        violations.add("Lab dip reference is required before sending dye order to supplier");
      }
      if (dye.approvalDocumentId() == null) {
        violations.add(
            "Approval document is required before sending dye order to supplier"
                + " (lab-dip must be approved first)");
      }
      if (dye.applicationMethod() == null || dye.applicationMethod().isBlank()) {
        violations.add("Application method is required before sending dye order to supplier");
      }
    }

    return violations;
  }
}
