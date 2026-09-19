package com.fabricmanagement.sales.salesorder.domain.requirement;

import com.fabricmanagement.product.core.domain.registry.UnitCode;
import com.fabricmanagement.product.core.domain.registry.policy.LinearDensityV1;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One typed requirement facet. The facet kind and comparison operator are closed vocabularies;
 * {@code value} retains the kind-specific structured payload without forcing unrelated facets into
 * one flat table.
 */
public record RequirementFacet(
    Kind kind,
    String qualifier,
    State state,
    Comparison comparison,
    RequirementFacetValue nominalValue,
    RequirementFacetValue value,
    DecisionBasis decisionBasis) {

  public enum Kind {
    CERTIFICATION,
    ORIGIN,
    COLOUR_IDENTITY,
    SHADE_APPROVAL,
    WIDTH,
    WEIGHT,
    YARN_COUNT,
    YARN_TWIST,
    YARN_CONSTRUCTION,
    FIBRE_GRADE,
    FIBRE_SHADE
  }

  public enum State {
    BOUNDED,
    UNCONSTRAINED,
    UNSPECIFIED
  }

  public enum Comparison {
    EXACT,
    MINIMUM,
    MAXIMUM,
    RANGE,
    ALL,
    ANY,
    SET_MEMBERSHIP,
    NONE
  }

  /** Traceable reason for an explicit facet value, including UNCONSTRAINED. */
  public record DecisionBasis(Source source, String reference, UUID actorId, Instant decidedAt) {
    public enum Source {
      SPEC_VERSION,
      CUSTOMER_INSTRUCTION,
      AUTHORISED_DECISION
    }

    public DecisionBasis {
      Objects.requireNonNull(source, "Facet decision source is required");
      if (reference == null || reference.isBlank() || actorId == null || decidedAt == null) {
        throw new IllegalArgumentException(
            "Facet decision basis requires reference, actor and timestamp");
      }
    }
  }

  public RequirementFacet {
    Objects.requireNonNull(kind, "Requirement facet kind is required");
    Objects.requireNonNull(state, "Requirement facet state is required");
    qualifier =
        qualifier == null || qualifier.isBlank()
            ? null
            : qualifier.strip().toUpperCase(Locale.ROOT);
    comparison = comparison == null ? Comparison.NONE : comparison;

    if (state == State.BOUNDED && value == null) {
      throw new IllegalArgumentException("BOUNDED facet requires a structured value: " + kind);
    }
    if (state == State.BOUNDED && comparison == Comparison.NONE) {
      throw new IllegalArgumentException("BOUNDED facet requires a comparison rule: " + kind);
    }
    if (state == State.BOUNDED && decisionBasis == null) {
      throw new IllegalArgumentException("BOUNDED facet requires a traceable decision basis");
    }
    if (value != null && !matchesKind(kind, value)) {
      throw new IllegalArgumentException(
          "Facet " + kind + " cannot carry value " + value.getClass().getSimpleName());
    }
    if (nominalValue != null && !matchesKind(kind, nominalValue)) {
      throw new IllegalArgumentException(
          "Facet " + kind + " cannot carry nominal " + nominalValue.getClass().getSimpleName());
    }
    if (state == State.BOUNDED) {
      validateBoundedValue(kind, comparison, value);
    }
    if (state == State.UNCONSTRAINED && decisionBasis == null) {
      throw new IllegalArgumentException("UNCONSTRAINED facet requires a traceable decision basis");
    }
    if (state != State.BOUNDED && value != null) {
      throw new IllegalArgumentException(state + " facet must not carry a bounded value: " + kind);
    }
    if (state != State.BOUNDED && comparison != Comparison.NONE) {
      throw new IllegalArgumentException(state + " facet must use comparison NONE: " + kind);
    }
  }

  public RequirementFacet(
      Kind kind,
      String qualifier,
      State state,
      Comparison comparison,
      RequirementFacetValue value,
      DecisionBasis decisionBasis) {
    this(kind, qualifier, state, comparison, null, value, decisionBasis);
  }

  public String identity() {
    return kind.name() + ":" + (qualifier == null ? "" : qualifier);
  }

  private static boolean matchesKind(Kind kind, RequirementFacetValue candidate) {
    return switch (kind) {
      case CERTIFICATION -> candidate instanceof RequirementFacetValue.Certification;
      case ORIGIN -> candidate instanceof RequirementFacetValue.Origin;
      case COLOUR_IDENTITY -> candidate instanceof RequirementFacetValue.ColourIdentity;
      case SHADE_APPROVAL -> candidate instanceof RequirementFacetValue.ShadeApproval;
      case WIDTH -> candidate instanceof RequirementFacetValue.Width;
      case WEIGHT -> candidate instanceof RequirementFacetValue.Weight;
      case YARN_COUNT -> candidate instanceof RequirementFacetValue.YarnCount;
      case YARN_TWIST -> candidate instanceof RequirementFacetValue.YarnTwist;
      case YARN_CONSTRUCTION -> candidate instanceof RequirementFacetValue.YarnConstruction;
      case FIBRE_GRADE, FIBRE_SHADE -> candidate instanceof RequirementFacetValue.Categorical;
    };
  }

  private static void validateBoundedValue(
      Kind kind, Comparison comparison, RequirementFacetValue candidate) {
    boolean comparisonAllowed =
        switch (kind) {
          case CERTIFICATION -> comparison == Comparison.ALL || comparison == Comparison.ANY;
          case ORIGIN -> comparison == Comparison.SET_MEMBERSHIP;
          case COLOUR_IDENTITY, SHADE_APPROVAL -> comparison == Comparison.EXACT;
          case WIDTH, WEIGHT -> true;
          case YARN_COUNT, YARN_TWIST ->
              comparison == Comparison.EXACT
                  || comparison == Comparison.MINIMUM
                  || comparison == Comparison.MAXIMUM
                  || comparison == Comparison.RANGE;
          case YARN_CONSTRUCTION ->
              comparison == Comparison.EXACT
                  || comparison == Comparison.ALL
                  || comparison == Comparison.ANY;
          case FIBRE_GRADE, FIBRE_SHADE ->
              comparison == Comparison.EXACT
                  || comparison == Comparison.ALL
                  || comparison == Comparison.ANY
                  || comparison == Comparison.SET_MEMBERSHIP;
        };
    if (!comparisonAllowed) {
      throw new IllegalArgumentException(
          "Comparison " + comparison + " is not valid for facet " + kind);
    }
    if (candidate instanceof RequirementFacetValue.Width width) {
      validateNumericComparison(comparison, width.bounds().boundType());
    } else if (candidate instanceof RequirementFacetValue.Weight weight) {
      validateNumericComparison(comparison, weight.bounds().boundType());
    } else if (candidate instanceof RequirementFacetValue.YarnCount count
        && (count.originalSystem() == null
            || count.originalValue() == null
            || count.countBasis() == null
            || count.context() == null
            || count.resultantTex() == null
            || count.resultantTexRule() == null)) {
      throw new IllegalArgumentException(
          "BOUNDED yarn count requires source value and resultant tex");
    } else if (candidate instanceof RequirementFacetValue.YarnCount count) {
      validateNumericComparison(comparison, count.resultantTexRule().boundType());
      validateYarnCount(count);
    } else if (candidate instanceof RequirementFacetValue.YarnTwist twist
        && (twist.stages().isEmpty()
            || twist.stages().stream()
                .noneMatch(
                    stage ->
                        stage.direction().state() == State.BOUNDED
                            || stage.turnsPerMetre().state() == State.BOUNDED))) {
      throw new IllegalArgumentException("BOUNDED yarn twist requires a bounded sub-value");
    } else if (candidate instanceof RequirementFacetValue.YarnTwist twist) {
      twist.stages().stream()
          .map(RequirementFacetValue.TwistStage::turnsPerMetre)
          .filter(subValue -> subValue.state() == State.BOUNDED)
          .forEach(subValue -> validateNumericComparison(comparison, subValue.value().boundType()));
    } else if (candidate instanceof RequirementFacetValue.YarnConstruction construction
        && construction.structureType().state() != State.BOUNDED
        && construction.foldCount().state() != State.BOUNDED
        && construction.spinningSystem().state() != State.BOUNDED
        && construction.features().state() != State.BOUNDED) {
      throw new IllegalArgumentException("BOUNDED yarn construction requires a bounded sub-value");
    } else if (candidate instanceof RequirementFacetValue.Categorical categorical
        && comparison == Comparison.EXACT
        && categorical.values().size() != 1) {
      throw new IllegalArgumentException("EXACT categorical comparison requires one value");
    }
  }

  private static void validateYarnCount(RequirementFacetValue.YarnCount count) {
    if (!"tex".equals(count.resultantTexRule().unit())) {
      throw new IllegalArgumentException("Yarn-count comparison bounds must be expressed in tex");
    }
    if (!"RESULTANT".equalsIgnoreCase(count.countBasis())
        && !"COMPONENT".equalsIgnoreCase(count.countBasis())) {
      throw new IllegalArgumentException("Yarn-count basis must be RESULTANT or COMPONENT");
    }
    if (count.resultantTex().signum() <= 0) {
      throw new IllegalArgumentException("Resultant yarn count must be positive");
    }

    UnitCode sourceUnit;
    try {
      sourceUnit = UnitCode.valueOf(count.originalSystem().strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException invalidUnit) {
      throw new IllegalArgumentException(
          "Unsupported yarn-count system: " + count.originalSystem(), invalidUnit);
    }
    BigDecimal canonical;
    try {
      canonical = LinearDensityV1.INSTANCE.toCanonical(count.originalValue(), sourceUnit);
    } catch (RuntimeException invalidValue) {
      throw new IllegalArgumentException("Invalid source yarn count", invalidValue);
    }
    if ("RESULTANT".equalsIgnoreCase(count.countBasis())
        && canonical
                .setScale(2, RoundingMode.HALF_UP)
                .compareTo(count.resultantTex().setScale(2, RoundingMode.HALF_UP))
            != 0) {
      throw new IllegalArgumentException(
          "Resultant tex contradicts the source yarn count after canonical conversion");
    }
    if (!contains(count.resultantTexRule(), count.resultantTex())) {
      throw new IllegalArgumentException("Resultant tex must satisfy its comparison bounds");
    }
  }

  private static boolean contains(RequirementFacetValue.NumericBounds bounds, BigDecimal value) {
    return switch (bounds.boundType()) {
      case EXACT -> value.compareTo(bounds.exact()) == 0;
      case MIN ->
          bounds.minimumInclusive()
              ? value.compareTo(bounds.minimum()) >= 0
              : value.compareTo(bounds.minimum()) > 0;
      case MAX ->
          bounds.maximumInclusive()
              ? value.compareTo(bounds.maximum()) <= 0
              : value.compareTo(bounds.maximum()) < 0;
      case RANGE ->
          (bounds.minimumInclusive()
                  ? value.compareTo(bounds.minimum()) >= 0
                  : value.compareTo(bounds.minimum()) > 0)
              && (bounds.maximumInclusive()
                  ? value.compareTo(bounds.maximum()) <= 0
                  : value.compareTo(bounds.maximum()) < 0);
    };
  }

  private static void validateNumericComparison(
      Comparison comparison, RequirementFacetValue.BoundType boundType) {
    boolean consistent =
        switch (boundType) {
          case EXACT -> comparison == Comparison.EXACT;
          case MIN -> comparison == Comparison.MINIMUM;
          case MAX -> comparison == Comparison.MAXIMUM;
          case RANGE -> comparison == Comparison.RANGE;
        };
    if (!consistent) {
      throw new IllegalArgumentException(
          "Numeric bound " + boundType + " conflicts with comparison " + comparison);
    }
  }
}
