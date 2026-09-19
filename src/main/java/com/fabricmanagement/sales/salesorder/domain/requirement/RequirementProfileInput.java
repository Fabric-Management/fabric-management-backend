package com.fabricmanagement.sales.salesorder.domain.requirement;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Caller-visible profile input. Identity, version, completeness and fingerprint are server-owned.
 */
public record RequirementProfileInput(
    RequirementProfileBasis basis,
    String scopeVersion,
    String resolutionRuleVersion,
    Set<String> scope,
    List<RequirementFacet> facets,
    List<UnmodelledSpecConstraint> unmodelledConstraints,
    List<RequirementDeviation> deviations) {

  public RequirementProfileInput {
    if (basis == null
        || scopeVersion == null
        || scopeVersion.isBlank()
        || resolutionRuleVersion == null
        || resolutionRuleVersion.isBlank()) {
      throw new IllegalArgumentException(
          "Profile basis, scope version and resolution-rule version are required");
    }
    scope =
        scope == null
            ? Set.of()
            : scope.stream()
                .map(
                    identity -> {
                      if (identity == null || identity.isBlank()) {
                        throw new IllegalArgumentException(
                            "Profile scope identity must not be blank");
                      }
                      return identity.strip().toUpperCase(Locale.ROOT);
                    })
                .collect(Collectors.toCollection(TreeSet::new));
    scope = java.util.Collections.unmodifiableSet(scope);
    facets =
        facets == null
            ? List.of()
            : facets.stream().sorted(Comparator.comparing(RequirementFacet::identity)).toList();
    unmodelledConstraints =
        unmodelledConstraints == null
            ? List.of()
            : unmodelledConstraints.stream()
                .sorted(Comparator.comparing(UnmodelledSpecConstraint::field))
                .toList();
    deviations =
        deviations == null
            ? List.of()
            : deviations.stream()
                .sorted(
                    Comparator.comparing(RequirementDeviation::facetIdentity)
                        .thenComparing(RequirementDeviation::reference)
                        .thenComparing(RequirementDeviation::actorId)
                        .thenComparing(RequirementDeviation::decidedAt))
                .toList();
  }
}
