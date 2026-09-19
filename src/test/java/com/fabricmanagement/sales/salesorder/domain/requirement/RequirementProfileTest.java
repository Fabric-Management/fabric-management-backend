package com.fabricmanagement.sales.salesorder.domain.requirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequirementProfileTest {

  private static final UUID ACTOR = UUID.randomUUID();
  private static final Instant DECIDED_AT = Instant.parse("2026-09-18T10:00:00Z");

  @Test
  void derivesCompletenessFromScopeAndKeepsEvidenceUnknownSeparate() {
    RequirementProfileSnapshot profile =
        RequirementProfileSnapshot.resolve(
            UUID.randomUUID(),
            1,
            input(
                List.of(
                    bounded(RequirementFacet.Kind.WIDTH, "finished"),
                    unconstrained(RequirementFacet.Kind.ORIGIN, "fabric_made")),
                Set.of("WIDTH:FINISHED", "ORIGIN:FABRIC_MADE"),
                List.of(
                    new UnmodelledSpecConstraint(
                        "composition",
                        UnmodelledSpecConstraint.Status.RESOLVED_UNSUPPORTED,
                        JsonNodeFactory.instance.textNode("100% CO"),
                        "material composition",
                        "Known pinned composition",
                        "No lot comparator"))),
            null,
            false);

    assertThat(profile.complete()).isTrue();
    assertThat(profile.unmodelledConstraints()).hasSize(1);
  }

  @Test
  void missingFacetAndAmbiguousPinnedValueMakeProfileIncomplete() {
    RequirementProfileSnapshot profile =
        RequirementProfileSnapshot.resolve(
            UUID.randomUUID(),
            1,
            input(
                List.of(bounded(RequirementFacet.Kind.WIDTH, "finished")),
                Set.of("WIDTH:FINISHED", "WEIGHT:FINISHED"),
                List.of(
                    new UnmodelledSpecConstraint(
                        "filamentForm",
                        UnmodelledSpecConstraint.Status.AMBIGUOUS_MEANING,
                        null,
                        null,
                        null,
                        "Legacy source has no vocabulary"))),
            null,
            false);

    assertThat(profile.complete()).isFalse();
    assertThat(profile.incompleteReasons())
        .containsExactly("UNSPECIFIED:WEIGHT:FINISHED", "UNRESOLVED_SPEC:filamentForm");
  }

  @Test
  void kindLevelScopeRequiresEveryExplicitFacetOfThatKindToBeResolved() {
    RequirementFacet unspecifiedFiberOrigin =
        new RequirementFacet(
            RequirementFacet.Kind.ORIGIN,
            "fiber_grown",
            RequirementFacet.State.UNSPECIFIED,
            RequirementFacet.Comparison.NONE,
            null,
            null);
    RequirementProfileSnapshot profile =
        RequirementProfileSnapshot.resolve(
            UUID.randomUUID(),
            1,
            input(
                List.of(
                    unconstrained(RequirementFacet.Kind.ORIGIN, "fabric_made"),
                    unspecifiedFiberOrigin),
                Set.of("ORIGIN"),
                List.of()),
            null,
            false);

    assertThat(profile.complete()).isFalse();
    assertThat(profile.incompleteReasons()).containsExactly("UNSPECIFIED:ORIGIN");
  }

  @Test
  void unconstrainedWithoutTraceableBasisIsRejected() {
    assertThatThrownBy(
            () ->
                new RequirementFacet(
                    RequirementFacet.Kind.ORIGIN,
                    "fabric_made",
                    RequirementFacet.State.UNCONSTRAINED,
                    RequirementFacet.Comparison.NONE,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traceable decision basis");
  }

  @Test
  void profileFingerprintChangesWhenPinnedBasisChanges() {
    RequirementProfileInput first =
        input(
            List.of(bounded(RequirementFacet.Kind.WIDTH, "finished")),
            Set.of("WIDTH:FINISHED"),
            List.of());
    RequirementProfileInput second =
        new RequirementProfileInput(
            new RequirementProfileBasis(
                RequirementProfileBasis.Kind.AUTHORISED_DECISION,
                null,
                null,
                null,
                null,
                ACTOR,
                DECIDED_AT,
                "decision-2"),
            first.scopeVersion(),
            first.resolutionRuleVersion(),
            first.scope(),
            first.facets(),
            first.unmodelledConstraints(),
            first.deviations());

    String firstFingerprint =
        RequirementProfileSnapshot.resolve(UUID.randomUUID(), 1, first, null, false).fingerprint();
    String secondFingerprint =
        RequirementProfileSnapshot.resolve(UUID.randomUUID(), 1, second, null, false).fingerprint();

    assertThat(secondFingerprint).isNotEqualTo(firstFingerprint);
  }

  @Test
  void semanticallyEquivalentCollectionOrderHasOneFingerprint() {
    RequirementFacet width = bounded(RequirementFacet.Kind.WIDTH, "finished");
    RequirementFacet origin = unconstrained(RequirementFacet.Kind.ORIGIN, "fabric_made");
    RequirementProfileInput first =
        input(List.of(width, origin), Set.of("WIDTH:FINISHED", "ORIGIN:FABRIC_MADE"), List.of());
    RequirementProfileInput reordered =
        input(
            List.of(origin, width),
            new java.util.LinkedHashSet<>(List.of("ORIGIN:FABRIC_MADE", "WIDTH:FINISHED")),
            List.of());

    String firstFingerprint =
        RequirementProfileSnapshot.resolve(UUID.randomUUID(), 1, first, null, false).fingerprint();
    String reorderedFingerprint =
        RequirementProfileSnapshot.resolve(UUID.randomUUID(), 1, reordered, null, false)
            .fingerprint();

    assertThat(reorderedFingerprint).isEqualTo(firstFingerprint);
  }

  private static RequirementProfileInput input(
      List<RequirementFacet> facets,
      Set<String> scope,
      List<UnmodelledSpecConstraint> constraints) {
    return new RequirementProfileInput(
        new RequirementProfileBasis(
            RequirementProfileBasis.Kind.LINE_EXPLICIT,
            null,
            null,
            null,
            null,
            ACTOR,
            DECIDED_AT,
            "line-contract"),
        "fabric-v1",
        "sales-req-v1",
        scope,
        facets,
        constraints,
        List.of());
  }

  private static RequirementFacet bounded(RequirementFacet.Kind kind, String qualifier) {
    return new RequirementFacet(
        kind,
        qualifier,
        RequirementFacet.State.BOUNDED,
        RequirementFacet.Comparison.MINIMUM,
        new RequirementFacetValue.Width(
            new RequirementFacetValue.NumericBounds(
                RequirementFacetValue.BoundType.MIN,
                null,
                new BigDecimal("150.0"),
                null,
                true,
                false,
                "cm"),
            RequirementFacetValue.WidthForm.OPEN,
            RequirementFacetValue.MaterialState.FINISHED),
        new RequirementFacet.DecisionBasis(
            RequirementFacet.DecisionBasis.Source.CUSTOMER_INSTRUCTION,
            "customer-contract",
            ACTOR,
            DECIDED_AT));
  }

  private static RequirementFacet unconstrained(RequirementFacet.Kind kind, String qualifier) {
    return new RequirementFacet(
        kind,
        qualifier,
        RequirementFacet.State.UNCONSTRAINED,
        RequirementFacet.Comparison.NONE,
        null,
        new RequirementFacet.DecisionBasis(
            RequirementFacet.DecisionBasis.Source.AUTHORISED_DECISION,
            "fixture-decision",
            ACTOR,
            DECIDED_AT));
  }
}
