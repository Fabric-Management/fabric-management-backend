package com.fabricmanagement.procurement.purchaseorder.app.validation;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.FiberPurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates {@link FiberPurchaseSpecs} against textile domain rules.
 *
 * <p>Create phase: grade must be A/B/C/SECOND (AGENTS.md Quality Grades), moisture 0-100%.
 *
 * <p>Confirm phase: grade is mandatory.
 */
@Component
public class FiberPurchaseOrderValidator implements PurchaseOrderValidator {

  private static final Set<String> VALID_GRADES = Set.of("A", "B", "C", "SECOND");

  @Override
  public PurchaseOrderModuleType getSupportedType() {
    return PurchaseOrderModuleType.FIBER;
  }

  @Override
  public List<String> validateOnCreate(PurchaseOrderSpecs specs) {
    var violations = new ArrayList<String>();

    if (specs instanceof FiberPurchaseSpecs fiber) {
      if (fiber.grade() != null && !VALID_GRADES.contains(fiber.grade().toUpperCase())) {
        violations.add("Fiber grade must be one of: A, B, C, SECOND, got: " + fiber.grade());
      }
      if (fiber.moistureContent() != null
          && (fiber.moistureContent() < 0 || fiber.moistureContent() > 100)) {
        violations.add(
            "Moisture content must be between 0 and 100%, got: " + fiber.moistureContent());
      }
    }

    return violations;
  }

  @Override
  public List<String> validateOnConfirm(PurchaseOrderSpecs specs) {
    // Grade format is already validated in validateOnCreate (engine re-runs it).
    // Here we only check presence (completeness).
    var violations = new ArrayList<String>();

    if (specs instanceof FiberPurchaseSpecs fiber) {
      if (fiber.grade() == null || fiber.grade().isBlank()) {
        violations.add("Fiber grade is required before sending to supplier");
      }
    }

    return violations;
  }
}
