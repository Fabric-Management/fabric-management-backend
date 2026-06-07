package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fiber Validation Service - Business rule validations for fibers.
 *
 * <p>Centralizes all validation logic for fiber creation/updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberValidationService {

  private final FiberRepository fiberRepository;

  // =====================================================
  // VALIDATION RULES FOR BLENDED FIBERS
  // =====================================================

  /**
   * Validate composition percentages.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>Total must be exactly 100%
   *   <li>No negative percentages
   *   <li>No zero percentages
   *   <li>Each percentage ≤ 100%
   * </ul>
   */
  @Transactional(readOnly = true)
  public void validateCompositionPercentages(Map<UUID, BigDecimal> composition) {
    if (composition == null || composition.isEmpty()) {
      throw new FiberDomainException("Composition cannot be empty", "FIBER_COMPOSITION_EMPTY", 400);
    }

    // Check total is 100%
    double total = composition.values().stream().mapToDouble(BigDecimal::doubleValue).sum();

    if (Math.abs(total - FiberConstants.TOTAL_PERCENTAGE) > FiberConstants.PERCENTAGE_TOLERANCE) {
      throw new FiberDomainException(
          "Composition percentages must sum to exactly 100%",
          "FIBER_COMPOSITION_TOTAL_NOT_100", 400, new Object[] {total});
    }

    // Check each percentage
    for (Map.Entry<UUID, BigDecimal> entry : composition.entrySet()) {
      BigDecimal percentage = entry.getValue();

      // No negative percentages
      if (percentage.compareTo(BigDecimal.ZERO) < 0) {
        throw new FiberDomainException(
            "Percentage cannot be negative",
            "FIBER_COMPOSITION_NEGATIVE_PERCENTAGE",
            400,
            new Object[] {percentage});
      }

      // No zero percentages (use composition at all)
      if (percentage.compareTo(BigDecimal.ZERO) == 0) {
        throw new FiberDomainException(
            "Composition entries cannot be 0%", "FIBER_COMPOSITION_ZERO_PERCENTAGE", 400);
      }

      // No percentages over 100%
      BigDecimal maxPercentage = BigDecimal.valueOf(FiberConstants.TOTAL_PERCENTAGE);
      if (percentage.compareTo(maxPercentage) > 0) {
        throw new FiberDomainException(
            "Percentage cannot exceed 100%",
            "FIBER_COMPOSITION_MAX_EXCEEDED", 400, new Object[] {percentage});
      }
    }
  }

  /**
   * Validate minimum composition ratio.
   *
   * <p>Rule: No single fiber can be less than 5% (to avoid trace amounts).
   */
  @Transactional(readOnly = true)
  public void validateMinimumRatio(Map<UUID, BigDecimal> composition, double minPercentage) {
    for (Map.Entry<UUID, BigDecimal> entry : composition.entrySet()) {
      BigDecimal percentage = entry.getValue();

      if (percentage.doubleValue() < minPercentage) {
        throw new FiberDomainException(
            "Fiber percentage too low",
            "FIBER_COMPOSITION_MIN_RATIO_NOT_MET",
            400,
            new Object[] {minPercentage, percentage.doubleValue()});
      }
    }
  }

  /**
   * Validate maximum number of components in blend.
   *
   * <p>Rule: Blended fiber cannot have more than 5 components.
   */
  @Transactional(readOnly = true)
  public void validateMaxComponents(Map<UUID, BigDecimal> composition, int maxComponents) {
    if (composition.size() > maxComponents) {
      throw new FiberDomainException(
          "Blend cannot have more than maximum allowed components",
          "FIBER_COMPOSITION_MAX_COMPONENTS_EXCEEDED",
          400,
          new Object[] {maxComponents, composition.size()});
    }
  }

  /**
   * Validate that base fibers are not circular references.
   *
   * <p>Rule: Base fiber cannot be another blended fiber (to prevent recursion).
   */
  @Transactional(readOnly = true)
  public void validateNoCircularReferences(Map<UUID, BigDecimal> composition) {
    List<Fiber> baseFibers = fiberRepository.findAllById(composition.keySet());

    for (Fiber baseFiber : baseFibers) {
      if (baseFiber.isBlended()) {
        throw new IllegalArgumentException(
            String.format(
                "Base fiber '%s' is also a blend. Cannot blend already-blended fibers.",
                baseFiber.getFiberName()));
      }
    }
  }

  /**
   * Validate that base fibers are accessible to the current tenant.
   *
   * <p>Rule: All base fibers must belong to the current tenant. Each tenant has its own cloned
   * fiber catalog — no cross-tenant access allowed.
   */
  @Transactional(readOnly = true)
  public void validateTenantConsistency(Map<UUID, BigDecimal> composition, UUID currentTenantId) {
    List<Fiber> baseFibers = fiberRepository.findAllById(composition.keySet());

    for (Fiber baseFiber : baseFibers) {
      UUID fiberTenantId = baseFiber.getTenantId();
      if (!currentTenantId.equals(fiberTenantId)) {
        throw new IllegalArgumentException(
            "All base fibers must belong to your organization's catalog");
      }
    }
  }

  /**
   * Validate that base fibers are active.
   *
   * <p>Rule: Cannot create blend from deactivated fibers.
   */
  @Transactional(readOnly = true)
  public void validateBaseFibersActive(Map<UUID, BigDecimal> composition) {
    List<Fiber> baseFibers = fiberRepository.findAllById(composition.keySet());

    if (baseFibers.size() != composition.size()) {
      throw new IllegalArgumentException("One or more base fibers not found in the system");
    }

    for (Fiber baseFiber : baseFibers) {
      if (!Boolean.TRUE.equals(baseFiber.getIsActive())) {
        throw new IllegalArgumentException(
            String.format("Base fiber '%s' is not active", baseFiber.getFiberName()));
      }
    }
  }

  /**
   * Validate fiber name format.
   *
   * <p>Rule: Fiber name must be 3-255 characters.
   */
  public void validateFiberNameFormat(String fiberName) {
    if (fiberName == null || fiberName.isBlank()) {
      return;
    }

    if (fiberName.length() < FiberConstants.MIN_FIBER_NAME_LENGTH
        || fiberName.length() > FiberConstants.MAX_FIBER_NAME_LENGTH) {
      throw new IllegalArgumentException(
          String.format(
              "Fiber name must be between %d and %d characters",
              FiberConstants.MIN_FIBER_NAME_LENGTH, FiberConstants.MAX_FIBER_NAME_LENGTH));
    }
  }

  /**
   * Validate category compatibility with composition.
   *
   * <p>Rule: Blended fiber category should be compatible with base fiber categories.
   */
  @Transactional(readOnly = true)
  public void validateCategoryCompatibility(
      UUID blendedCategoryId, Map<UUID, BigDecimal> composition) {
    if (blendedCategoryId == null) {
      throw new IllegalArgumentException("Blended fiber category must be specified for validation");
    }

    List<Fiber> baseFibers = fiberRepository.findAllById(composition.keySet());
    for (Fiber baseFiber : baseFibers) {
      if (baseFiber.getFiberCategory() == null) {
        log.warn(
            "Base fiber '{}' has no category assigned. Category compatibility skipped for this component.",
            baseFiber.getFiberName());
      }
    }
    // Further complex category compatibility rules (e.g. blend can't be natural if
    // components are synthetic)
    // can be added here matching business requirements.
  }
}
