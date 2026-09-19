package com.fabricmanagement.sales.salesorder.domain.requirement;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/** Closed, OpenAPI-visible payload family for typed requirement facets. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "valueType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = RequirementFacetValue.Certification.class, name = "CERTIFICATION"),
  @JsonSubTypes.Type(value = RequirementFacetValue.Origin.class, name = "ORIGIN"),
  @JsonSubTypes.Type(value = RequirementFacetValue.ColourIdentity.class, name = "COLOUR_IDENTITY"),
  @JsonSubTypes.Type(value = RequirementFacetValue.ShadeApproval.class, name = "SHADE_APPROVAL"),
  @JsonSubTypes.Type(value = RequirementFacetValue.Width.class, name = "WIDTH"),
  @JsonSubTypes.Type(value = RequirementFacetValue.Weight.class, name = "WEIGHT"),
  @JsonSubTypes.Type(value = RequirementFacetValue.YarnCount.class, name = "YARN_COUNT"),
  @JsonSubTypes.Type(value = RequirementFacetValue.YarnTwist.class, name = "YARN_TWIST"),
  @JsonSubTypes.Type(
      value = RequirementFacetValue.YarnConstruction.class,
      name = "YARN_CONSTRUCTION"),
  @JsonSubTypes.Type(value = RequirementFacetValue.Categorical.class, name = "CATEGORICAL")
})
@Schema(
    oneOf = {
      RequirementFacetValue.Certification.class,
      RequirementFacetValue.Origin.class,
      RequirementFacetValue.ColourIdentity.class,
      RequirementFacetValue.ShadeApproval.class,
      RequirementFacetValue.Width.class,
      RequirementFacetValue.Weight.class,
      RequirementFacetValue.YarnCount.class,
      RequirementFacetValue.YarnTwist.class,
      RequirementFacetValue.YarnConstruction.class,
      RequirementFacetValue.Categorical.class
    })
public sealed interface RequirementFacetValue
    permits RequirementFacetValue.Certification,
        RequirementFacetValue.Origin,
        RequirementFacetValue.ColourIdentity,
        RequirementFacetValue.ShadeApproval,
        RequirementFacetValue.Width,
        RequirementFacetValue.Weight,
        RequirementFacetValue.YarnCount,
        RequirementFacetValue.YarnTwist,
        RequirementFacetValue.YarnConstruction,
        RequirementFacetValue.Categorical {

  record Certification(List<CertificateRef> certificates) implements RequirementFacetValue {
    public Certification {
      certificates =
          certificates == null
              ? List.of()
              : certificates.stream()
                  .sorted(
                      Comparator.comparing(CertificateRef::scheme)
                          .thenComparing(CertificateRef::certificateKind))
                  .toList();
      if (certificates.isEmpty()) {
        throw new IllegalArgumentException("Certification requirement needs at least one kind");
      }
      if (certificates.stream().distinct().count() != certificates.size()) {
        throw new IllegalArgumentException("Certification requirement contains a duplicate kind");
      }
    }
  }

  record CertificateRef(String scheme, String certificateKind) {
    public CertificateRef {
      if (scheme == null
          || scheme.isBlank()
          || certificateKind == null
          || certificateKind.isBlank()) {
        throw new IllegalArgumentException("Certificate scheme and kind are required");
      }
      scheme = scheme.strip().toUpperCase(Locale.ROOT);
      certificateKind = certificateKind.strip().toUpperCase(Locale.ROOT);
    }
  }

  record Origin(
      OriginSubject originSubject,
      Set<String> allowedCountries,
      CountrySetReference countrySet,
      MixtureRule mixtureRule)
      implements RequirementFacetValue {
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

    public Origin {
      if (originSubject == null || mixtureRule == null) {
        throw new IllegalArgumentException("Origin subject and mixture rule are required");
      }
      allowedCountries =
          allowedCountries == null
              ? Set.of()
              : allowedCountries.stream()
                  .map(
                      country -> {
                        if (country == null) {
                          throw new IllegalArgumentException("Origin country must not be null");
                        }
                        return country.strip().toUpperCase(Locale.ROOT);
                      })
                  .collect(
                      java.util.stream.Collectors.collectingAndThen(
                          java.util.stream.Collectors.toCollection(TreeSet::new),
                          Collections::unmodifiableSet));
      if ((allowedCountries.isEmpty()) == (countrySet == null)) {
        throw new IllegalArgumentException(
            "Origin requires either ISO countries or one versioned country set");
      }
      if (allowedCountries.stream().anyMatch(country -> !COUNTRY.matcher(country).matches())) {
        throw new IllegalArgumentException("Origin countries must use ISO 3166-1 alpha-2");
      }
    }
  }

  enum OriginSubject {
    FIBRE_GROWN,
    YARN_SPUN,
    FABRIC_MADE
  }

  enum MixtureRule {
    SINGLE_ORIGIN,
    ALL_ORIGINS_ALLOWED
  }

  record CountrySetReference(String name, int version) {
    public CountrySetReference {
      if (name == null || name.isBlank() || version < 1) {
        throw new IllegalArgumentException("Country-set name and positive version are required");
      }
    }
  }

  record ColourIdentity(UUID colorId) implements RequirementFacetValue {
    public ColourIdentity {
      if (colorId == null) throw new IllegalArgumentException("Colour-card identity is required");
    }
  }

  record ShadeApproval(boolean required, ApprovalKind approvalKind)
      implements RequirementFacetValue {
    public ShadeApproval {
      if (required && approvalKind == null) {
        throw new IllegalArgumentException("Required shade approval needs an approval kind");
      }
    }
  }

  enum ApprovalKind {
    LAB_DIP,
    BULK_LOT,
    EITHER
  }

  record Width(NumericBounds bounds, WidthForm form, MaterialState materialState)
      implements RequirementFacetValue {
    public Width {
      if (bounds == null || form == null || materialState == null) {
        throw new IllegalArgumentException("Width bounds, form and material state are required");
      }
      if (!"cm".equals(bounds.unit())) {
        throw new IllegalArgumentException("Usable width must be expressed in cm");
      }
    }
  }

  enum WidthForm {
    OPEN,
    TUBULAR
  }

  enum MaterialState {
    FINISHED,
    GREIGE
  }

  record Weight(NumericBounds bounds) implements RequirementFacetValue {
    public Weight {
      if (bounds == null || !"g/m²".equals(bounds.unit())) {
        throw new IllegalArgumentException("Fabric areal mass must be expressed in g/m²");
      }
    }
  }

  record NumericBounds(
      BoundType boundType,
      BigDecimal exact,
      BigDecimal minimum,
      BigDecimal maximum,
      boolean minimumInclusive,
      boolean maximumInclusive,
      String unit) {
    public NumericBounds {
      if (boundType == null || unit == null || unit.isBlank()) {
        throw new IllegalArgumentException("Numeric bound type and unit are required");
      }
      boolean valid =
          switch (boundType) {
            case EXACT -> exact != null && minimum == null && maximum == null;
            case MIN -> exact == null && minimum != null && maximum == null;
            case MAX -> exact == null && minimum == null && maximum != null;
            case RANGE ->
                exact == null
                    && minimum != null
                    && maximum != null
                    && minimum.compareTo(maximum) <= 0;
          };
      if (!valid) throw new IllegalArgumentException("Numeric values do not match bound type");
    }
  }

  enum BoundType {
    EXACT,
    MIN,
    MAX,
    RANGE
  }

  record YarnCount(
      String originalSystem,
      BigDecimal originalValue,
      String countBasis,
      String context,
      BigDecimal resultantTex,
      NumericBounds resultantTexRule)
      implements RequirementFacetValue {
    public YarnCount(
        String originalSystem,
        BigDecimal originalValue,
        String countBasis,
        String context,
        BigDecimal resultantTex) {
      this(originalSystem, originalValue, countBasis, context, resultantTex, null);
    }
  }

  record YarnTwist(List<TwistStage> stages) implements RequirementFacetValue {
    public YarnTwist {
      stages =
          stages == null
              ? List.of()
              : stages.stream().sorted(Comparator.comparing(TwistStage::sequence)).toList();
      if (stages.stream().map(TwistStage::sequence).distinct().count() != stages.size()) {
        throw new IllegalArgumentException("Yarn twist contains a duplicate stage sequence");
      }
    }
  }

  record TwistStage(
      String stage,
      SubValue<String> direction,
      SubValue<NumericBounds> turnsPerMetre,
      Integer sequence) {
    public TwistStage {
      if (stage == null
          || stage.isBlank()
          || direction == null
          || turnsPerMetre == null
          || sequence == null
          || sequence < 1) {
        throw new IllegalArgumentException(
            "Twist stage requires stage, positive sequence and explicit sub-value states");
      }
    }
  }

  record SubValue<T>(RequirementFacet.State state, T value) {
    public SubValue {
      if (state == null) throw new IllegalArgumentException("Sub-value state is required");
      if (state == RequirementFacet.State.BOUNDED && value == null) {
        throw new IllegalArgumentException("BOUNDED sub-value requires a value");
      }
      if (state != RequirementFacet.State.BOUNDED && value != null) {
        throw new IllegalArgumentException(state + " sub-value must not carry a bounded value");
      }
    }
  }

  record YarnConstruction(
      SubValue<String> structureType,
      SubValue<Integer> foldCount,
      SubValue<SpinningSystem> spinningSystem,
      SubValue<Set<String>> features)
      implements RequirementFacetValue {
    public YarnConstruction {
      if (structureType == null
          || foldCount == null
          || spinningSystem == null
          || features == null) {
        throw new IllegalArgumentException(
            "Yarn construction requires explicit states for every sub-value");
      }
      if (features.value() != null) {
        features =
            new SubValue<>(
                features.state(),
                Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(features.value()))));
      }
    }
  }

  record SpinningSystem(UUID id, String code, String family) {
    public SpinningSystem {
      if (id == null || code == null || code.isBlank() || family == null || family.isBlank()) {
        throw new IllegalArgumentException(
            "Spinning system requires catalogue identity, code and canonical family");
      }
    }
  }

  record Categorical(Set<String> values) implements RequirementFacetValue {
    public Categorical {
      values =
          values == null
              ? Set.of()
              : values.stream()
                  .map(
                      value -> {
                        if (value == null || value.isBlank()) {
                          throw new IllegalArgumentException(
                              "Categorical values must not be null or blank");
                        }
                        return value.strip().toUpperCase(Locale.ROOT);
                      })
                  .collect(
                      java.util.stream.Collectors.collectingAndThen(
                          java.util.stream.Collectors.toCollection(TreeSet::new),
                          sorted -> Collections.unmodifiableSet(new LinkedHashSet<>(sorted))));
      if (values.isEmpty()) throw new IllegalArgumentException("Categorical values are required");
    }
  }
}
