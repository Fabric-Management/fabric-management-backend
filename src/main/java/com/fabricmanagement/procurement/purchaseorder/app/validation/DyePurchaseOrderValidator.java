package com.fabricmanagement.procurement.purchaseorder.app.validation;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.DyePurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Validates {@link DyePurchaseSpecs} for dye/finishing purchase orders.
 *
 * <p>Create phase: no strict rules (draft can be saved with partial info while lab-dip is
 * in-progress).
 *
 * <p>Confirm phase: colorCode and approvalDocumentId are mandatory. A dye PO cannot be sent to a
 * supplier without an approved lab-dip result.
 */
@Component
public class DyePurchaseOrderValidator implements PurchaseOrderValidator {

  @Override
  public PurchaseOrderModuleType getSupportedType() {
    return PurchaseOrderModuleType.DYE_FINISHING;
  }

  @Override
  public List<String> validateOnCreate(PurchaseOrderSpecs specs) {
    // No create-phase rules for dye — lab-dip process is iterative,
    // drafts are saved with partial data intentionally
    return List.of();
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
    }

    return violations;
  }
}
