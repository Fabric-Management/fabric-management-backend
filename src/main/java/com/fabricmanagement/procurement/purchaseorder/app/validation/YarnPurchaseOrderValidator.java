package com.fabricmanagement.procurement.purchaseorder.app.validation;

import static com.fabricmanagement.procurement.purchaseorder.app.validation.TextileValidationConstants.*;

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
 * <p>Create phase: twist direction must be S or Z. Enforces positive boundaries for TPI, CSP,
 * Uster%, and cone weight.
 *
 * <p>Confirm phase: yarnCount and composition are mandatory.
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
      if (yarn.tpi() != null && yarn.tpi() <= 0) {
        violations.add("TPI must be greater than 0, got: " + yarn.tpi());
      }
      if (yarn.csp() != null && yarn.csp() <= 0) {
        violations.add("CSP must be greater than 0, got: " + yarn.csp());
      }
      if (yarn.usterUPercentage() != null
          && (yarn.usterUPercentage() < USTER_MIN || yarn.usterUPercentage() > USTER_MAX)) {
        violations.add(
            "Uster U% must be between "
                + USTER_MIN
                + " and "
                + USTER_MAX
                + "%, got: "
                + yarn.usterUPercentage());
      }
      if (yarn.coneWeight() != null && yarn.coneWeight() <= 0) {
        violations.add("Cone weight must be greater than 0, got: " + yarn.coneWeight());
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
      if (yarn.composition() == null || yarn.composition().isBlank()) {
        violations.add("Composition is required before sending to supplier");
      }
    }

    return violations;
  }
}
