package com.fabricmanagement.sales.salesorder.domain.requirement;

import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Fully resolved, immutable profile version used by evidence and production decisions. */
public record RequirementProfileSnapshot(
    UUID profileId,
    int profileVersion,
    RequirementProfileBasis basis,
    String scopeVersion,
    String resolutionRuleVersion,
    Set<String> scope,
    List<RequirementFacet> facets,
    List<UnmodelledSpecConstraint> unmodelledConstraints,
    List<RequirementDeviation> deviations,
    JsonNode pinnedSource,
    boolean complete,
    List<String> incompleteReasons,
    String fingerprint) {

  public RequirementProfileSnapshot {
    scope = Set.copyOf(scope);
    facets = List.copyOf(facets);
    unmodelledConstraints = List.copyOf(unmodelledConstraints);
    deviations = List.copyOf(deviations);
    pinnedSource = pinnedSource == null ? null : pinnedSource.deepCopy();
    incompleteReasons = List.copyOf(incompleteReasons);
  }

  public JsonNode pinnedSource() {
    return pinnedSource == null ? null : pinnedSource.deepCopy();
  }

  /** Rebuild input from the persisted basis, resolved values and recorded deviations. */
  public RequirementProfileInput reproductionInput() {
    return new RequirementProfileInput(
        basis,
        scopeVersion,
        resolutionRuleVersion,
        scope,
        facets,
        unmodelledConstraints,
        deviations);
  }

  public static RequirementProfileSnapshot resolve(
      UUID profileId,
      int version,
      RequirementProfileInput input,
      JsonNode pinnedSource,
      boolean residualModuleSpecs) {
    if (profileId == null || version < 1) {
      throw new IllegalArgumentException("Profile identity and positive version are required");
    }

    Set<String> identities = new HashSet<>();
    input
        .facets()
        .forEach(
            facet -> {
              if (!identities.add(facet.identity())) {
                throw new IllegalArgumentException(
                    "Duplicate requirement facet: " + facet.identity());
              }
            });

    List<String> incomplete = new ArrayList<>();
    input.scope().stream()
        .filter(identity -> !scopeIsResolved(identity, input.facets()))
        .sorted()
        .map(identity -> "UNSPECIFIED:" + identity)
        .forEach(incomplete::add);
    input.unmodelledConstraints().stream()
        .filter(UnmodelledSpecConstraint::makesProfileIncomplete)
        .map(constraint -> "UNRESOLVED_SPEC:" + constraint.field())
        .sorted()
        .forEach(incomplete::add);
    if (residualModuleSpecs) {
      incomplete.add("UNTYPED_REQUIREMENTS");
    }

    var content =
        new FingerprintContent(
            input.basis(),
            input.scopeVersion(),
            input.resolutionRuleVersion(),
            input.scope(),
            input.facets(),
            input.unmodelledConstraints(),
            input.deviations(),
            pinnedSource,
            incomplete);
    String fingerprint = CanonicalJsonFingerprint.of(content);
    return new RequirementProfileSnapshot(
        profileId,
        version,
        input.basis(),
        input.scopeVersion(),
        input.resolutionRuleVersion(),
        input.scope(),
        input.facets(),
        input.unmodelledConstraints(),
        input.deviations(),
        pinnedSource,
        incomplete.isEmpty(),
        incomplete,
        fingerprint);
  }

  private static boolean scopeMatches(String scopeIdentity, RequirementFacet facet) {
    return scopeIdentity.contains(":")
        ? facet.identity().equals(scopeIdentity)
        : facet.kind().name().equals(scopeIdentity);
  }

  private static boolean scopeIsResolved(String scopeIdentity, List<RequirementFacet> facets) {
    List<RequirementFacet> matches =
        facets.stream().filter(facet -> scopeMatches(scopeIdentity, facet)).toList();
    return !matches.isEmpty()
        && matches.stream().allMatch(facet -> facet.state() != RequirementFacet.State.UNSPECIFIED);
  }

  private record FingerprintContent(
      RequirementProfileBasis basis,
      String scopeVersion,
      String resolutionRuleVersion,
      Set<String> scope,
      List<RequirementFacet> facets,
      List<UnmodelledSpecConstraint> constraints,
      List<RequirementDeviation> deviations,
      JsonNode pinnedSource,
      List<String> incompleteReasons) {}
}
