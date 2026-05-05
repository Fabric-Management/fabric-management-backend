package com.fabricmanagement.procurement.purchaseorder.app.validation;

import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.DyePurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.FabricPurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.FiberPurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.GenericPurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.YarnPurchaseSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Registry and dispatcher for module-specific PurchaseOrder validators.
 *
 * <p>Collects all {@link PurchaseOrderValidator} beans via Spring injection and dispatches
 * validation calls to the appropriate strategy based on {@link PurchaseOrderModuleType}.
 *
 * <p>If no validator is registered for a given type (e.g. GENERIC), validation passes silently
 * (Open-Closed Principle).
 *
 * <p><b>Usage points in PurchaseOrderService:</b>
 *
 * <ul>
 *   <li>{@code createPurchaseOrder()} → {@link #validateOnCreate}
 *   <li>{@code changeStatus(SENT)} → {@link #validateOnConfirm}
 *   <li>Future: {@code updateModuleSpecs()} → {@link #validateOnCreate}
 * </ul>
 */
@Component
@Slf4j
public class PurchaseOrderValidationEngine {

  private final Map<PurchaseOrderModuleType, PurchaseOrderValidator> validators;

  public PurchaseOrderValidationEngine(List<PurchaseOrderValidator> validatorList) {
    this.validators =
        validatorList.stream()
            .collect(
                Collectors.toMap(
                    PurchaseOrderValidator::getSupportedType,
                    Function.identity(),
                    (existing, duplicate) -> {
                      throw new IllegalStateException(
                          "Duplicate PurchaseOrderValidator for type: "
                              + existing.getSupportedType());
                    }));

    log.info(
        "PurchaseOrderValidationEngine initialized with {} validators: {}",
        validators.size(),
        validators.keySet());
  }

  /**
   * Runs format/range validations. Called on PO creation and any moduleSpecs update.
   *
   * @throws ProcurementDomainException if any violations are found
   */
  public void validateOnCreate(PurchaseOrderModuleType type, PurchaseOrderSpecs specs) {
    if (type == null || specs == null) return;

    validateTypeSpecsAlignment(type, specs);

    var validator = validators.get(type);
    if (validator == null) return;

    List<String> violations = validator.validateOnCreate(specs);
    throwIfViolated(violations);
  }

  /**
   * Runs completeness validations for DRAFT→SENT transition. Re-runs create rules as a safety net
   * (specs may have been modified between create and confirm).
   *
   * @throws ProcurementDomainException if any violations are found
   */
  public void validateOnConfirm(PurchaseOrderModuleType type, PurchaseOrderSpecs specs) {
    if (type == null || specs == null) return;

    validateTypeSpecsAlignment(type, specs);

    var validator = validators.get(type);
    if (validator == null) return;

    var violations = new ArrayList<String>();
    violations.addAll(validator.validateOnCreate(specs));
    violations.addAll(validator.validateOnConfirm(specs));
    throwIfViolated(violations);
  }

  /**
   * Ensures moduleType and specs type are aligned. Prevents silent validation bypass when frontend
   * sends mismatched type/specs (e.g. type=FABRIC with YarnPurchaseSpecs).
   */
  private void validateTypeSpecsAlignment(PurchaseOrderModuleType type, PurchaseOrderSpecs specs) {
    boolean aligned =
        switch (type) {
          case FIBER -> specs instanceof FiberPurchaseSpecs;
          case YARN -> specs instanceof YarnPurchaseSpecs;
          case FABRIC -> specs instanceof FabricPurchaseSpecs;
          case DYE_FINISHING -> specs instanceof DyePurchaseSpecs;
          case GENERIC -> specs instanceof GenericPurchaseSpecs;
        };
    if (!aligned) {
      throw new ProcurementDomainException(
          "Module type "
              + type
              + " does not match specs type: "
              + specs.getClass().getSimpleName());
    }
  }

  private void throwIfViolated(List<String> violations) {
    if (!violations.isEmpty()) {
      throw new ProcurementDomainException(
          "PurchaseOrder validation failed: " + String.join("; ", violations));
    }
  }
}
