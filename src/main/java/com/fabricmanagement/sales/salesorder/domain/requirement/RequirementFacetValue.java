package com.fabricmanagement.sales.salesorder.domain.requirement;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

  // The discriminated subtypes keep the runtime any-setter but publish no
  // additionalProperties:false:
  // their schemas are allOf the parent, where valueType lives, so the flag would reject every
  // valid value. Unknown properties are still refused when the request is read.
  @JsonIgnoreProperties(ignoreUnknown = false)
  record Certification(List<CertificateRef> certificates) implements RequirementFacetValue {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown Certification property: " + name);
    }

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

  @JsonIgnoreProperties(ignoreUnknown = false)
  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  record CertificateRef(String scheme, String certificateKind) {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown CertificateRef property: " + name);
    }

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

  @JsonIgnoreProperties(ignoreUnknown = false)
  record Origin(
      OriginSubject originSubject,
      Set<String> allowedCountries,
      CountrySetReference countrySet,
      MixtureRule mixtureRule)
      implements RequirementFacetValue {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown Origin property: " + name);
    }

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

  @JsonIgnoreProperties(ignoreUnknown = false)
  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  record CountrySetReference(String name, int version) {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown CountrySetReference property: " + name);
    }

    public CountrySetReference {
      if (name == null || name.isBlank() || version < 1) {
        throw new IllegalArgumentException("Country-set name and positive version are required");
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = false)
  record ColourIdentity(UUID colorId) implements RequirementFacetValue {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown ColourIdentity property: " + name);
    }

    public ColourIdentity {
      if (colorId == null) throw new IllegalArgumentException("Colour-card identity is required");
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = false)
  record ShadeApproval(boolean required, ApprovalKind approvalKind)
      implements RequirementFacetValue {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown ShadeApproval property: " + name);
    }

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

  @JsonIgnoreProperties(ignoreUnknown = false)
  record Width(NumericBounds bounds, WidthForm form, MaterialState materialState)
      implements RequirementFacetValue {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown Width property: " + name);
    }

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

  @JsonIgnoreProperties(ignoreUnknown = false)
  record Weight(NumericBounds bounds) implements RequirementFacetValue {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown Weight property: " + name);
    }

    public Weight {
      if (bounds == null || !"g/m²".equals(bounds.unit())) {
        throw new IllegalArgumentException("Fabric areal mass must be expressed in g/m²");
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = false)
  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  record NumericBounds(
      BoundType boundType,
      BigDecimal exact,
      BigDecimal minimum,
      BigDecimal maximum,
      boolean minimumInclusive,
      boolean maximumInclusive,
      String unit) {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown NumericBounds property: " + name);
    }

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

  @JsonIgnoreProperties(ignoreUnknown = false)
  record YarnCount(
      String originalSystem,
      BigDecimal originalValue,
      String countBasis,
      String context,
      BigDecimal resultantTex,
      NumericBounds resultantTexRule)
      implements RequirementFacetValue {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown YarnCount property: " + name);
    }

    public YarnCount(
        String originalSystem,
        BigDecimal originalValue,
        String countBasis,
        String context,
        BigDecimal resultantTex) {
      this(originalSystem, originalValue, countBasis, context, resultantTex, null);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = false)
  record YarnTwist(List<TwistStage> stages) implements RequirementFacetValue {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown YarnTwist property: " + name);
    }

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

  @JsonIgnoreProperties(ignoreUnknown = false)
  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  record TwistStage(
      String stage,
      SubValue<String> direction,
      SubValue<NumericBounds> turnsPerMetre,
      Integer sequence) {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown TwistStage property: " + name);
    }

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

  @JsonIgnoreProperties(ignoreUnknown = false)
  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  record SubValue<T>(RequirementFacet.State state, T value) {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown SubValue property: " + name);
    }

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

  @JsonIgnoreProperties(ignoreUnknown = false)
  record YarnConstruction(
      SubValue<String> structureType,
      SubValue<Integer> foldCount,
      SubValue<SpinningSystem> spinningSystem,
      SubValue<Set<String>> features)
      implements RequirementFacetValue {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown YarnConstruction property: " + name);
    }

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

  @JsonIgnoreProperties(ignoreUnknown = false)
  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  record SpinningSystem(UUID id, String code, String family) {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown SpinningSystem property: " + name);
    }

    public SpinningSystem {
      if (id == null || code == null || code.isBlank() || family == null || family.isBlank()) {
        throw new IllegalArgumentException(
            "Spinning system requires catalogue identity, code and canonical family");
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = false)
  record Categorical(Set<String> values) implements RequirementFacetValue {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
      throw new IllegalArgumentException("Unknown Categorical property: " + name);
    }

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
