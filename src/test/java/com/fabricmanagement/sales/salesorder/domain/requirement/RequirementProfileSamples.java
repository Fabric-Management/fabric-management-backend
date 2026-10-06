package com.fabricmanagement.sales.salesorder.domain.requirement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared test samples of typed requirement profiles: one facet of every value type. */
public final class RequirementProfileSamples {

  private RequirementProfileSamples() {}

  /**
   * A valid profile input with one facet of every valueType (S15.16), decided by {@code actor}; the
   * colour facet names {@code colour}.
   */
  public static RequirementProfileInput everyValueType(UUID actor, UUID colour) {
    RequirementFacet.DecisionBasis decision =
        new RequirementFacet.DecisionBasis(
            RequirementFacet.DecisionBasis.Source.CUSTOMER_INSTRUCTION,
            "customer-contract",
            actor,
            Instant.parse("2026-09-18T10:00:00Z"));
    List<RequirementFacet> facets =
        List.of(
            new RequirementFacet(
                RequirementFacet.Kind.CERTIFICATION,
                null,
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.ALL,
                new RequirementFacetValue.Certification(
                    List.of(new RequirementFacetValue.CertificateRef("GOTS", "ORGANIC"))),
                decision),
            new RequirementFacet(
                RequirementFacet.Kind.ORIGIN,
                "fibre",
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.SET_MEMBERSHIP,
                new RequirementFacetValue.Origin(
                    RequirementFacetValue.OriginSubject.FIBRE_GROWN,
                    Set.of("GB", "PT"),
                    null,
                    RequirementFacetValue.MixtureRule.ALL_ORIGINS_ALLOWED),
                decision),
            new RequirementFacet(
                RequirementFacet.Kind.COLOUR_IDENTITY,
                null,
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.EXACT,
                new RequirementFacetValue.ColourIdentity(colour),
                decision),
            new RequirementFacet(
                RequirementFacet.Kind.SHADE_APPROVAL,
                null,
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.EXACT,
                new RequirementFacetValue.ShadeApproval(
                    true, RequirementFacetValue.ApprovalKind.LAB_DIP),
                decision),
            new RequirementFacet(
                RequirementFacet.Kind.WIDTH,
                "finished",
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.MINIMUM,
                new RequirementFacetValue.Width(
                    bounds(RequirementFacetValue.BoundType.MIN, "150", "cm"),
                    RequirementFacetValue.WidthForm.OPEN,
                    RequirementFacetValue.MaterialState.FINISHED),
                decision),
            new RequirementFacet(
                RequirementFacet.Kind.WEIGHT,
                "finished",
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.EXACT,
                new RequirementFacetValue.Weight(
                    bounds(RequirementFacetValue.BoundType.EXACT, "180", "g/m²")),
                decision),
            new RequirementFacet(
                RequirementFacet.Kind.YARN_COUNT,
                "resultant",
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.EXACT,
                new RequirementFacetValue.YarnCount(
                    "NE",
                    new BigDecimal("30"),
                    "RESULTANT",
                    "SINGLE",
                    new BigDecimal("19.68"),
                    bounds(RequirementFacetValue.BoundType.EXACT, "19.68", "tex")),
                decision),
            new RequirementFacet(
                RequirementFacet.Kind.YARN_TWIST,
                "single-direction",
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.EXACT,
                new RequirementFacetValue.YarnTwist(
                    List.of(
                        new RequirementFacetValue.TwistStage(
                            "SINGLE",
                            new RequirementFacetValue.SubValue<>(
                                RequirementFacet.State.BOUNDED, "Z"),
                            new RequirementFacetValue.SubValue<>(
                                RequirementFacet.State.UNSPECIFIED, null),
                            1))),
                decision),
            new RequirementFacet(
                RequirementFacet.Kind.YARN_CONSTRUCTION,
                null,
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.EXACT,
                new RequirementFacetValue.YarnConstruction(
                    new RequirementFacetValue.SubValue<>(RequirementFacet.State.BOUNDED, "SINGLE"),
                    new RequirementFacetValue.SubValue<>(RequirementFacet.State.UNSPECIFIED, null),
                    new RequirementFacetValue.SubValue<>(RequirementFacet.State.UNSPECIFIED, null),
                    new RequirementFacetValue.SubValue<>(
                        RequirementFacet.State.BOUNDED, Set.of("COMPACT"))),
                decision),
            new RequirementFacet(
                RequirementFacet.Kind.FIBRE_GRADE,
                null,
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.EXACT,
                new RequirementFacetValue.Categorical(Set.of("LONG_STAPLE")),
                decision));
    return new RequirementProfileInput(
        new RequirementProfileBasis(
            RequirementProfileBasis.Kind.LINE_EXPLICIT,
            null,
            null,
            null,
            null,
            actor,
            Instant.parse("2026-09-18T10:00:00Z"),
            "line-explicit"),
        "fabric-v1",
        "sales-req-v1",
        Set.of("WIDTH:FINISHED"),
        facets,
        List.of(),
        List.of());
  }

  private static RequirementFacetValue.NumericBounds bounds(
      RequirementFacetValue.BoundType type, String value, String unit) {
    BigDecimal number = new BigDecimal(value);
    return switch (type) {
      case EXACT ->
          new RequirementFacetValue.NumericBounds(type, number, null, null, true, true, unit);
      case MIN ->
          new RequirementFacetValue.NumericBounds(type, null, number, null, true, false, unit);
      case MAX ->
          new RequirementFacetValue.NumericBounds(type, null, null, number, false, true, unit);
      case RANGE -> throw new IllegalArgumentException("Not used here");
    };
  }
}
