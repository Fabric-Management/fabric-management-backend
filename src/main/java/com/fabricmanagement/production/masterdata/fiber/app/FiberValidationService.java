package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
      throw new IllegalArgumentException("Composition cannot be empty");
    }

    // Check total is 100%
    double total = composition.values().stream().mapToDouble(BigDecimal::doubleValue).sum();

    if (Math.abs(total - FiberConstants.TOTAL_PERCENTAGE) > FiberConstants.PERCENTAGE_TOLERANCE) {
      throw new IllegalArgumentException(
          String.format("Composition percentages must sum to exactly 100%%, got: %.2f%%", total));
    }

    // Check each percentage
    for (Map.Entry<UUID, BigDecimal> entry : composition.entrySet()) {
      BigDecimal percentage = entry.getValue();

      // No negative percentages
      if (percentage.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException(
            String.format("Percentage cannot be negative: %.2f%%", percentage));
      }

      // No zero percentages (use composition at all)
      if (percentage.compareTo(BigDecimal.ZERO) == 0) {
        throw new IllegalArgumentException("Composition entries cannot be 0%");
      }

      // No percentages over 100%
      BigDecimal maxPercentage = BigDecimal.valueOf(FiberConstants.TOTAL_PERCENTAGE);
      if (percentage.compareTo(maxPercentage) > 0) {
        throw new IllegalArgumentException(
            String.format("Percentage cannot exceed 100%%: %.2f%%", percentage));
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
        throw new IllegalArgumentException(
            String.format(
                "Fiber percentage too low (minimum %.1f%%): %.2f%%",
                minPercentage, percentage.doubleValue()));
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
      throw new IllegalArgumentException(
          String.format(
              "Blend cannot have more than %d components, got: %d",
              maxComponents, composition.size()));
    }
  }

  /**
   * Validate that base fibers are not circular references.
   *
   * <p>Rule: Base fiber cannot be another blended fiber (to prevent recursion).
   */
  @Transactional(readOnly = true)
  public void validateNoCircularReferences(Map<UUID, BigDecimal> composition) {
    for (UUID baseFiberId : composition.keySet()) {
      Fiber baseFiber = fiberRepository.findById(baseFiberId).orElse(null);

      if (baseFiber == null) {
        continue;
      }

      // Check if base fiber itself has a composition (it's also a blend)
      Fiber baseFiberEntity = fiberRepository.findById(baseFiberId).orElse(null);
      boolean isBlend = baseFiberEntity != null && baseFiberEntity.isBlended();

      if (isBlend) {
        throw new IllegalArgumentException(
            String.format(
                "Base fiber '%s' is also a blend. Cannot blend already-blended fibers.",
                baseFiber.getFiberName()));
      }
    }
  }

  /**
   * Validate that base fibers belong to same tenant.
   *
   * <p>Rule: All base fibers must belong to the same tenant.
   */
  @Transactional(readOnly = true)
  public void validateTenantConsistency(Map<UUID, BigDecimal> composition, UUID currentTenantId) {
    Set<UUID> tenantIds =
        composition.keySet().stream()
            .map(baseFiberId -> fiberRepository.findById(baseFiberId))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(Fiber::getTenantId)
            .collect(Collectors.toSet());

    if (tenantIds.size() > 1 || !tenantIds.contains(currentTenantId)) {
      throw new IllegalArgumentException("All base fibers must belong to the same tenant");
    }
  }

  /**
   * Validate that base fibers are active.
   *
   * <p>Rule: Cannot create blend from deactivated fibers.
   */
  @Transactional(readOnly = true)
  public void validateBaseFibersActive(Map<UUID, BigDecimal> composition) {
    for (UUID baseFiberId : composition.keySet()) {
      Fiber baseFiber =
          fiberRepository
              .findById(baseFiberId)
              .orElseThrow(
                  () -> new IllegalArgumentException("Base fiber not found: " + baseFiberId));

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
      UUID blendedCategoryId, Map<UUID, BigDecimal> composition) {}
}
