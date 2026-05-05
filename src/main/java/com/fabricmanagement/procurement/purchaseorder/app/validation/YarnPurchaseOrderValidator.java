package com.fabricmanagement.procurement.purchaseorder.app.validation;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.YarnPurchaseSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates {@link YarnPurchaseSpecs} against textile domain rules.
 *
 * <p>Create phase: twist direction must be S or Z.
 *
 * <p>Confirm phase: yarnCount is mandatory.
 */
@Component
public class YarnPurchaseOrderValidator implements PurchaseOrderValidator {

  private static final Set<String> VALID_TWISTS = Set.of("S", "Z");

  @Override
  public PurchaseOrderModuleType getSupportedType() {
    return PurchaseOrderModuleType.YARN;
  }

  @Override
  public List<String> validateOnCreate(PurchaseOrderSpecs specs) {
    var violations = new ArrayList<String>();

    if (specs instanceof YarnPurchaseSpecs yarn) {
      if (yarn.twist() != null && !VALID_TWISTS.contains(yarn.twist().toUpperCase())) {
        violations.add("Twist direction must be S or Z, got: " + yarn.twist());
      }
    }

    return violations;
  }

  @Override
  public List<String> validateOnConfirm(PurchaseOrderSpecs specs) {
    var violations = new ArrayList<String>();

    if (specs instanceof YarnPurchaseSpecs yarn) {
      if (yarn.yarnCount() == null || yarn.yarnCount().isBlank()) {
        violations.add("Yarn count is required before sending to supplier");
      }
    }

    return violations;
  }
}
