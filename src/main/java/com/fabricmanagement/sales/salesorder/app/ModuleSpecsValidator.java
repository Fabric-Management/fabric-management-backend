package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderLineRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates {@code moduleSpecs} JSONB content against the required fields per {@link ModuleType}.
 *
 * <p>Schema source: architecture doc {@code 03-sales/sales-order.md} — ModuleSpecs JSONB Şemaları.
 *
 * <ul>
 *   <li>FIBER — certificationReq, originReq
 *   <li>YARN — count, twist, construction
 *   <li>FABRIC — weight, width, weaveType
 *   <li>DYE_FINISHING — color
 * </ul>
 *
 * <p>Validation is intentionally non-exhaustive (not all module fields are required) — only the
 * "must-have" fields identified in the arch doc are enforced.
 */
@Component
public class ModuleSpecsValidator {

  private static final Map<String, RequirementFacet.Kind> LEGACY_REQUIREMENT_KEYS =
      Map.of(
          "certificationReq", RequirementFacet.Kind.CERTIFICATION,
          "originReq", RequirementFacet.Kind.ORIGIN,
          "weight", RequirementFacet.Kind.WEIGHT,
          "width", RequirementFacet.Kind.WIDTH,
          "color", RequirementFacet.Kind.COLOUR_IDENTITY,
          "count", RequirementFacet.Kind.YARN_COUNT,
          "twist", RequirementFacet.Kind.YARN_TWIST,
          "construction", RequirementFacet.Kind.YARN_CONSTRUCTION);

  /**
   * Validates the given line's {@code moduleSpecs} against its {@code moduleType}.
   *
   * @throws OrderDomainException if a required field is missing or specs are null when needed
   */
  public void validate(SalesOrderLineRequest line) {
    validate(line.getModuleType(), line.getModuleSpecs(), line.getRequirementProfile(), null);
  }

  /**
   * Validates the given line's {@code moduleSpecs} against its {@code moduleType} for an update
   * request.
   *
   * @throws OrderDomainException if a required field is missing or specs are null when needed
   */
  public void validate(UpdateSalesOrderLineRequest line) {
    validate(line.getModuleType(), line.getModuleSpecs(), line.getRequirementProfile(), null);
  }

  /**
   * Validates an existing line update against the effective typed profile. An omitted profile keeps
   * the current profile, so an older client cannot silently reintroduce a legacy requirement key.
   */
  public void validate(
      UpdateSalesOrderLineRequest line, RequirementProfileSnapshot currentRequirementProfile) {
    validate(
        line.getModuleType(),
        line.getModuleSpecs(),
        line.getRequirementProfile(),
        currentRequirementProfile);
  }

  private void validate(
      ModuleType type,
      Map<String, Object> specs,
      RequirementProfileInput requirementProfile,
      RequirementProfileSnapshot currentRequirementProfile) {
    Set<RequirementFacet.Kind> typedKinds =
        effectiveTypedKinds(requirementProfile, currentRequirementProfile);
    rejectConflictingLegacyKeys(specs, typedKinds);
    if (requirementProfile == null && currentRequirementProfile != null) {
      rejectAllLegacyRequirementKeys(specs);
    }
    if (!typedKinds.isEmpty() || requirementProfile != null || currentRequirementProfile != null) {
      return;
    }
    if (type == null) {
      return; // moduleType is optional at the line level — no specs to validate
    }

    switch (type) {
      case FIBER -> {
        // FIBER requires certificationReq (GOTS / OEKO-TEX etc.) and originReq
        requireField(specs, "certificationReq", type);
        requireField(specs, "originReq", type);
      }
      case YARN -> {
        // YARN requires count (e.g., "30/1 Ne"), twist (Z/S), construction
        requireField(specs, "count", type);
        requireField(specs, "twist", type);
        requireField(specs, "construction", type);
      }
      case FABRIC -> {
        // FABRIC requires weight (g/m²) and width (cm)
        requireField(specs, "weight", type);
        requireField(specs, "width", type);
      }
      case DYE_FINISHING -> {
        // DYE_FINISHING requires color (Pantone code or name)
        requireField(specs, "color", type);
      }
    }
  }

  private Set<RequirementFacet.Kind> effectiveTypedKinds(
      RequirementProfileInput requested, RequirementProfileSnapshot current) {
    if (requested == null && current == null) {
      return Set.of();
    }
    java.util.stream.Stream<RequirementFacet> effectiveFacets;
    if (requested == null) {
      effectiveFacets = current.facets().stream();
    } else if (current != null && current.basis().equals(requested.basis())) {
      effectiveFacets =
          java.util.stream.Stream.concat(current.facets().stream(), requested.facets().stream());
    } else {
      effectiveFacets = requested.facets().stream();
    }
    java.util.stream.Stream<RequirementFacet.Kind> scopedKinds =
        java.util.stream.Stream.concat(
                requested == null ? java.util.stream.Stream.empty() : requested.scope().stream(),
                current == null ? java.util.stream.Stream.empty() : current.scope().stream())
            .map(ModuleSpecsValidator::scopeKind)
            .filter(java.util.Objects::nonNull);
    return java.util.stream.Stream.concat(effectiveFacets.map(RequirementFacet::kind), scopedKinds)
        .collect(java.util.stream.Collectors.toSet());
  }

  private static RequirementFacet.Kind scopeKind(String identity) {
    String kind = identity.contains(":") ? identity.substring(0, identity.indexOf(':')) : identity;
    try {
      return RequirementFacet.Kind.valueOf(kind);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private void rejectAllLegacyRequirementKeys(Map<String, Object> specs) {
    if (specs == null || specs.isEmpty()) {
      return;
    }
    LEGACY_REQUIREMENT_KEYS.keySet().stream()
        .filter(specs::containsKey)
        .findFirst()
        .ifPresent(
            key -> {
              throw new OrderDomainException(
                  "moduleSpecs key '"
                      + key
                      + "' cannot be written while an existing typed requirement profile is retained");
            });
  }

  private void rejectConflictingLegacyKeys(
      Map<String, Object> specs, Set<RequirementFacet.Kind> typedKinds) {
    if (specs == null || specs.isEmpty() || typedKinds.isEmpty()) {
      return;
    }
    LEGACY_REQUIREMENT_KEYS.forEach(
        (key, kind) -> {
          if (specs.containsKey(key) && typedKinds.contains(kind)) {
            throw new OrderDomainException(
                "moduleSpecs key '" + key + "' conflicts with typed requirement facet " + kind);
          }
        });
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private void requireField(Map<String, Object> specs, String field, ModuleType type) {
    if (specs == null || !specs.containsKey(field) || specs.get(field) == null) {
      throw new OrderDomainException(
          String.format("moduleSpecs missing required field '%s' for moduleType %s", field, type));
    }
    Object val = specs.get(field);
    if (val instanceof String s && s.isBlank()) {
      throw new OrderDomainException(
          String.format("moduleSpecs field '%s' must not be blank for moduleType %s", field, type));
    }
  }
}
