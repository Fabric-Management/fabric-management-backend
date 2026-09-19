package com.fabricmanagement.sales.salesorder.domain.requirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FacetValueTest {

  @Test
  void boundedValueRequiresAnExplicitComparisonRule() {
    assertThatThrownBy(
            () ->
                new RequirementFacet(
                    RequirementFacet.Kind.WEIGHT,
                    "finished",
                    RequirementFacet.State.BOUNDED,
                    RequirementFacet.Comparison.NONE,
                    new RequirementFacetValue.Weight(
                        new RequirementFacetValue.NumericBounds(
                            RequirementFacetValue.BoundType.EXACT,
                            new BigDecimal("180"),
                            null,
                            null,
                            true,
                            true,
                            "g/m²")),
                    basis()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("comparison rule");
  }

  @Test
  void twistDirectionCanBeBoundedWithoutInventingTpm() {
    RequirementFacet direction =
        new RequirementFacet(
            RequirementFacet.Kind.YARN_TWIST,
            "single-direction",
            RequirementFacet.State.BOUNDED,
            RequirementFacet.Comparison.EXACT,
            new RequirementFacetValue.YarnTwist(
                java.util.List.of(
                    new RequirementFacetValue.TwistStage(
                        "SINGLE",
                        new RequirementFacetValue.SubValue<>(RequirementFacet.State.BOUNDED, "Z"),
                        new RequirementFacetValue.SubValue<>(
                            RequirementFacet.State.UNSPECIFIED, null),
                        1))),
            basis());

    RequirementFacetValue.YarnTwist twist = (RequirementFacetValue.YarnTwist) direction.value();
    assertThat(twist.stages().getFirst().turnsPerMetre().state())
        .isEqualTo(RequirementFacet.State.UNSPECIFIED);
    assertThat(direction.state()).isEqualTo(RequirementFacet.State.BOUNDED);
  }

  @Test
  void unboundedNumericPayloadCannotMasqueradeAsUnconstrained() {
    assertThatThrownBy(
            () ->
                new RequirementFacet(
                    RequirementFacet.Kind.WIDTH,
                    "finished-open",
                    RequirementFacet.State.UNCONSTRAINED,
                    RequirementFacet.Comparison.NONE,
                    width("150"),
                    basis()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not carry a bounded value");
  }

  @Test
  void nominalValueWithoutComparisonRuleStaysUnspecifiedAndIncomplete() {
    RequirementFacet nominalOnly =
        new RequirementFacet(
            RequirementFacet.Kind.YARN_COUNT,
            "resultant",
            RequirementFacet.State.UNSPECIFIED,
            RequirementFacet.Comparison.NONE,
            new RequirementFacetValue.YarnCount(
                "NE", new BigDecimal("30"), "RESULTANT", null, new BigDecimal("19.68")),
            null,
            basis());
    RequirementProfileInput input =
        new RequirementProfileInput(
            new RequirementProfileBasis(
                RequirementProfileBasis.Kind.LINE_EXPLICIT,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                Instant.parse("2026-09-18T10:00:00Z"),
                "contract"),
            "yarn-v1",
            "sales-req-v1",
            java.util.Set.of(nominalOnly.identity()),
            java.util.List.of(nominalOnly),
            java.util.List.of(),
            java.util.List.of());

    RequirementProfileSnapshot profile =
        RequirementProfileSnapshot.resolve(UUID.randomUUID(), 1, input, null, false);

    assertThat(profile.complete()).isFalse();
    assertThat(
            ((RequirementFacetValue.YarnCount) profile.facets().getFirst().nominalValue())
                .originalSystem())
        .isEqualTo("NE");
  }

  @Test
  void boundedResultantCountRejectsAClientSuppliedTexThatContradictsTheSourceCount() {
    assertThatThrownBy(
            () ->
                boundedYarnCount(
                    "NE",
                    "30",
                    "30",
                    new RequirementFacetValue.NumericBounds(
                        RequirementFacetValue.BoundType.EXACT,
                        new BigDecimal("30"),
                        null,
                        null,
                        true,
                        true,
                        "tex")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contradicts the source yarn count");
  }

  @Test
  void boundedResultantCountAcceptsTheServerCanonicalTexAtProductPrecision() {
    assertThatCode(
            () ->
                boundedYarnCount(
                    "NE",
                    "30",
                    "19.68",
                    new RequirementFacetValue.NumericBounds(
                        RequirementFacetValue.BoundType.EXACT,
                        new BigDecimal("19.68"),
                        null,
                        null,
                        true,
                        true,
                        "tex")))
        .doesNotThrowAnyException();
  }

  @Test
  void boundedYarnCountRequiresCanonicalTexBounds() {
    assertThatThrownBy(
            () ->
                boundedYarnCount(
                    "TEX",
                    "30",
                    "30",
                    new RequirementFacetValue.NumericBounds(
                        RequirementFacetValue.BoundType.EXACT,
                        new BigDecimal("30"),
                        null,
                        null,
                        true,
                        true,
                        "Ne")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be expressed in tex");
  }

  private RequirementFacet boundedYarnCount(
      String originalSystem,
      String originalValue,
      String resultantTex,
      RequirementFacetValue.NumericBounds bounds) {
    return new RequirementFacet(
        RequirementFacet.Kind.YARN_COUNT,
        "resultant",
        RequirementFacet.State.BOUNDED,
        RequirementFacet.Comparison.EXACT,
        new RequirementFacetValue.YarnCount(
            originalSystem,
            new BigDecimal(originalValue),
            "RESULTANT",
            "SINGLE",
            new BigDecimal(resultantTex),
            bounds),
        basis());
  }

  private RequirementFacetValue.Width width(String minimum) {
    return new RequirementFacetValue.Width(
        new RequirementFacetValue.NumericBounds(
            RequirementFacetValue.BoundType.MIN,
            null,
            new BigDecimal(minimum),
            null,
            true,
            false,
            "cm"),
        RequirementFacetValue.WidthForm.OPEN,
        RequirementFacetValue.MaterialState.FINISHED);
  }

  private RequirementFacet.DecisionBasis basis() {
    return new RequirementFacet.DecisionBasis(
        RequirementFacet.DecisionBasis.Source.CUSTOMER_INSTRUCTION,
        "contract",
        UUID.randomUUID(),
        Instant.parse("2026-09-18T10:00:00Z"));
  }
}
