package com.fabricmanagement.procurement.purchaseorder.app.validation;

import static com.fabricmanagement.procurement.purchaseorder.app.validation.TextileValidationConstants.*;

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
 * <p>Create phase: grade must be A/B/C/SECOND (AGENTS.md Quality Grades), moisture 0-20%. Validates
 * ranges for micronaire, strength, uniformity, and length bounds.
 *
 * <p>Confirm phase: grade and origin are mandatory.
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
          && (fiber.moistureContent() < MOISTURE_MIN || fiber.moistureContent() > MOISTURE_MAX)) {
        violations.add(
            "Moisture content must be between "
                + MOISTURE_MIN
                + " and "
                + MOISTURE_MAX
                + "%, got: "
                + fiber.moistureContent());
      }
      if (fiber.micronaire() != null
          && (fiber.micronaire() < MICRONAIRE_MIN || fiber.micronaire() > MICRONAIRE_MAX)) {
        violations.add(
            "Micronaire must be between "
                + MICRONAIRE_MIN
                + " and "
                + MICRONAIRE_MAX
                + ", got: "
                + fiber.micronaire());
      }
      if (fiber.stapleLength() != null && fiber.stapleLength() <= 0) {
        violations.add("Staple length must be greater than 0, got: " + fiber.stapleLength());
      }
      if (fiber.strength() != null && fiber.strength() <= 0) {
        violations.add("Strength must be greater than 0, got: " + fiber.strength());
      }
      if (fiber.uniformityIndex() != null
          && (fiber.uniformityIndex() < UNIFORMITY_MIN
              || fiber.uniformityIndex() > UNIFORMITY_MAX)) {
        violations.add(
            "Uniformity index must be between "
                + UNIFORMITY_MIN
                + " and "
                + UNIFORMITY_MAX
                + "%, got: "
                + fiber.uniformityIndex());
      }
      if (fiber.trashContent() != null
          && (fiber.trashContent() < TRASH_MIN || fiber.trashContent() > TRASH_MAX)) {
        violations.add(
            "Trash content must be between "
                + TRASH_MIN
                + " and "
                + TRASH_MAX
                + "%, got: "
                + fiber.trashContent());
      }
      if (fiber.colorGrade() != null && fiber.colorGrade().length() > COLOR_GRADE_MAX_LENGTH) {
        violations.add("Color grade must not exceed " + COLOR_GRADE_MAX_LENGTH + " characters");
      }
      if (fiber.cropYear() != null && fiber.cropYear().length() > CROP_YEAR_MAX_LENGTH) {
        violations.add("Crop year must not exceed " + CROP_YEAR_MAX_LENGTH + " characters");
      }
      if (fiber.origin() != null && fiber.origin().length() > ORIGIN_MAX_LENGTH) {
        violations.add("Origin must not exceed " + ORIGIN_MAX_LENGTH + " characters");
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
      if (fiber.origin() == null || fiber.origin().isBlank()) {
        violations.add("Origin is required before sending to supplier");
      }
    }

    return violations;
  }
}
