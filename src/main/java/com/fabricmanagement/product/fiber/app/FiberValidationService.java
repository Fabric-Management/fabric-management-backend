package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberComposition;
import com.fabricmanagement.product.fiber.domain.FiberStatus;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composition rules (FIBER-CATALOG-1), shared by blend definitions, batch overrides and quality
 * applicability queries.
 *
 * <p>Percentages use exact BigDecimal arithmetic: nulls are rejected, every share is positive and
 * at most 100, and the sum is exactly 100 with no tolerance. Components are Fiber IDs resolved in
 * one bulk query within {@code {current tenant, catalogue owner}}; a missing ID, another tenant's
 * private fibre and a Product ID supplied as a Fiber ID are all reported as not found, without
 * revealing ownership.
 */
@Service
@RequiredArgsConstructor
public class FiberValidationService {

  private final FiberRepository fiberRepository;

  /** Normalised composition together with its resolved component fibres. */
  public record ResolvedComposition(Map<UUID, BigDecimal> composition, Map<UUID, Fiber> fibers) {}

  /** A catalogue blend: at least two distinct active pure fibres, application limits apply. */
  @Transactional(readOnly = true)
  public ResolvedComposition validateBlendDefinition(
      Map<UUID, BigDecimal> composition, UUID tenantId) {
    requireEntries(composition);
    if (composition.size() < FiberConstants.MIN_BLEND_COMPONENTS) {
      throw new FiberDomainException(
          "A blend requires at least two distinct pure fibres",
          "FIBER_BLEND_MIN_COMPONENTS",
          400,
          new Object[] {FiberConstants.MIN_BLEND_COMPONENTS, composition.size()});
    }
    validateShares(composition);
    return new ResolvedComposition(
        FiberComposition.normalize(composition), resolveComponents(composition, tenantId));
  }

  /**
   * A physical (batch) composition: either one pure fibre at exactly 100% or a mixture that obeys
   * the blend-definition share rules.
   */
  @Transactional(readOnly = true)
  public ResolvedComposition validateEffectiveComposition(
      Map<UUID, BigDecimal> composition, UUID tenantId) {
    requireEntries(composition);
    validateShares(composition);
    return new ResolvedComposition(
        FiberComposition.normalize(composition), resolveComponents(composition, tenantId));
  }

  private static void requireEntries(Map<UUID, BigDecimal> composition) {
    if (composition == null || composition.isEmpty()) {
      throw new FiberDomainException("Composition cannot be empty", "FIBER_COMPOSITION_EMPTY", 400);
    }
  }

  private static void validateShares(Map<UUID, BigDecimal> composition) {
    if (composition.size() > FiberConstants.MAX_BLEND_COMPONENTS) {
      throw new FiberDomainException(
          "Composition exceeds the maximum number of components",
          "FIBER_COMPOSITION_MAX_COMPONENTS_EXCEEDED",
          400,
          new Object[] {FiberConstants.MAX_BLEND_COMPONENTS, composition.size()});
    }
    BigDecimal total = BigDecimal.ZERO;
    for (Map.Entry<UUID, BigDecimal> entry : composition.entrySet()) {
      if (entry.getKey() == null) {
        throw new FiberDomainException(
            "Every composition entry needs a Fiber id",
            "FIBER_COMPOSITION_COMPONENT_REQUIRED",
            400);
      }
      BigDecimal percentage = entry.getValue();
      if (percentage == null) {
        throw new FiberDomainException(
            "Every composition entry needs a percentage",
            "FIBER_COMPOSITION_PERCENTAGE_REQUIRED",
            400,
            new Object[] {entry.getKey()});
      }
      if (percentage.signum() < 0) {
        throw new FiberDomainException(
            "Percentage cannot be negative",
            "FIBER_COMPOSITION_NEGATIVE_PERCENTAGE",
            400,
            new Object[] {percentage});
      }
      if (percentage.signum() == 0) {
        throw new FiberDomainException(
            "Composition entries cannot be 0%", "FIBER_COMPOSITION_ZERO_PERCENTAGE", 400);
      }
      if (percentage.compareTo(FiberComposition.HUNDRED) > 0) {
        throw new FiberDomainException(
            "Percentage cannot exceed 100%",
            "FIBER_COMPOSITION_MAX_EXCEEDED", 400, new Object[] {percentage});
      }
      if (composition.size() > 1
          && percentage.compareTo(FiberConstants.MIN_COMPONENT_PERCENTAGE) < 0) {
        throw new FiberDomainException(
            "Component share is below the current minimum",
            "FIBER_COMPOSITION_MIN_RATIO_NOT_MET",
            400,
            new Object[] {FiberConstants.MIN_COMPONENT_PERCENTAGE, percentage});
      }
      total = total.add(percentage);
    }
    if (total.compareTo(FiberComposition.HUNDRED) != 0) {
      throw new FiberDomainException(
          "Composition percentages must sum to exactly 100%",
          "FIBER_COMPOSITION_TOTAL_NOT_100", 400, new Object[] {total.toPlainString()});
    }
  }

  /** One bulk resolution; every requested ID must be a visible, active, pure fibre. */
  private Map<UUID, Fiber> resolveComponents(Map<UUID, BigDecimal> composition, UUID tenantId) {
    Map<UUID, Fiber> found =
        fiberRepository
            .findScopedWithReferences(FiberCatalog.readScope(tenantId), composition.keySet())
            .stream()
            .collect(Collectors.toMap(Fiber::getId, Function.identity()));
    for (UUID id : composition.keySet()) {
      Fiber fiber = found.get(id);
      if (fiber == null) {
        throw new FiberDomainException(
            "Composition component is not an available fibre",
            "FIBER_COMPONENT_NOT_FOUND",
            400,
            new Object[] {id});
      }
      if (!Boolean.TRUE.equals(fiber.getIsActive()) || fiber.getStatus() != FiberStatus.ACTIVE) {
        throw new FiberDomainException(
            "Composition component is inactive or obsolete",
            "FIBER_COMPONENT_INACTIVE",
            400,
            new Object[] {id});
      }
      if (fiber.isBlended() || fiber.getFiberIsoCode() == null) {
        throw new FiberDomainException(
            "Composition components must be pure fibres; blends cannot be nested",
            "FIBER_COMPONENT_NOT_PURE",
            400,
            new Object[] {id});
      }
    }
    return found;
  }
}
