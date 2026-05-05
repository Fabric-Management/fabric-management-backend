package com.fabricmanagement.production.execution.workorder.app.validation;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.domain.specs.*;
import com.fabricmanagement.production.execution.workorder.domain.specs.WorkOrderProductionSpecs;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Dispatcher for WorkOrder production validation.
 *
 * <p>Uses the Strategy Pattern: gathers all registered {@link WorkOrderProductionValidator} beans
 * and dispatches validation to the correct one based on the module type.
 */
@Service
@Slf4j
public class WorkOrderProductionValidationEngine {

  private final Map<WorkOrderModuleType, WorkOrderProductionValidator> validators;

  public WorkOrderProductionValidationEngine(List<WorkOrderProductionValidator> validatorBeans) {
    this.validators =
        validatorBeans.stream()
            .collect(
                Collectors.toMap(
                    WorkOrderProductionValidator::getSupportedType, Function.identity()));
    log.info("Registered WorkOrder production validators for: {}", validators.keySet());
  }

  /** Dispatches create-phase validation. */
  public void validateOnCreate(WorkOrderModuleType type, WorkOrderProductionSpecs specs) {
    if (specs != null && !isSpecsMatchingType(type, specs)) {
      throw new WorkOrderDomainException(
          "Production specs type mismatch: moduleType="
              + type
              + " but specs is "
              + specs.getClass().getSimpleName());
    }

    WorkOrderProductionValidator validator = validators.get(type);

    if (validator != null) {
      List<String> violations = validator.validateOnCreate(specs);
      if (!violations.isEmpty()) {
        throw new WorkOrderDomainException(
            "Invalid production specifications for "
                + type
                + " creation: "
                + String.join(", ", violations));
      }
    }
  }

  /** Dispatches start-phase validation (completeness checks). */
  public void validateOnStart(WorkOrderModuleType type, WorkOrderProductionSpecs specs) {
    WorkOrderProductionValidator validator = validators.get(type);

    if (validator != null) {
      List<String> violations = validator.validateOnStart(specs);
      if (!violations.isEmpty()) {
        throw new WorkOrderDomainException(
            "Incomplete production specifications for "
                + type
                + " start: "
                + String.join(", ", violations));
      }
    }
  }

  private boolean isSpecsMatchingType(WorkOrderModuleType type, WorkOrderProductionSpecs specs) {
    return switch (type) {
      case SPINNING -> specs instanceof SpinningProductionSpecs;
      case WEAVING -> specs instanceof WeavingProductionSpecs;
      case KNITTING -> specs instanceof KnittingProductionSpecs;
      case DYEING -> specs instanceof DyeingProductionSpecs;
      case FINISHING -> specs instanceof FinishingProductionSpecs;
      case GENERIC -> specs instanceof GenericProductionSpecs;
    };
  }
}
