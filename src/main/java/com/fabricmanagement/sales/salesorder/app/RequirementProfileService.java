package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.app.ProductEvidenceQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.RequirementProfileVersion;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.port.YarnArticleSpecHistoryPort;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementDeviation;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileBasis;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.domain.requirement.UnmodelledSpecConstraint;
import com.fabricmanagement.sales.salesorder.infra.repository.RequirementProfileVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves and appends immutable order-line requirement profile versions. */
@Service
@RequiredArgsConstructor
public class RequirementProfileService {

  private static final Set<String> LEGACY_REQUIREMENT_KEYS =
      Set.of(
          "certificationReq",
          "originReq",
          "weight",
          "width",
          "color",
          "count",
          "twist",
          "construction");

  private final RequirementProfileVersionRepository versions;
  private final YarnArticleSpecHistoryPort yarnHistory;
  private final ProductEvidenceQueryService products;

  @Transactional
  public RequirementProfileSnapshot apply(
      SalesOrderLine line, RequirementProfileInput input, Map<String, Object> residualModuleSpecs) {
    if (line.getId() == null) {
      throw new IllegalArgumentException("Sales order line must be persisted before its profile");
    }
    if (input.basis().productId() != null
        && !input.basis().productId().equals(line.getProductId())) {
      throw new OrderDomainException("Requirement profile basis product does not match the line");
    }

    UUID profileId =
        line.getRequirementProfileId() == null ? UUID.randomUUID() : line.getRequirementProfileId();
    int nextVersion =
        line.getRequirementProfileVersion() == null
            ? 1
            : Math.addExact(line.getRequirementProfileVersion(), 1);
    RequirementProfileInput effectiveInput = mergeWithCurrent(line, applyServerScope(line, input));
    JsonNode pinnedSource = pinnedSource(effectiveInput.basis());
    effectiveInput = withPinnedYarnConstraints(effectiveInput, pinnedSource);
    effectiveInput = withPinnedYarnNominals(effectiveInput, pinnedSource);
    validateDeviations(effectiveInput);
    RequirementProfileSnapshot candidate =
        RequirementProfileSnapshot.resolve(
            profileId,
            nextVersion,
            effectiveInput,
            pinnedSource,
            hasResidualRequirementSpecs(residualModuleSpecs));

    if (candidate.fingerprint().equals(line.getRequirementProfileFingerprint())) {
      return line.getRequirementProfileSnapshot();
    }

    versions.save(
        RequirementProfileVersion.builder()
            .profileId(profileId)
            .profileVersion(nextVersion)
            .salesOrderLineId(line.getId())
            .fingerprint(candidate.fingerprint())
            .snapshot(candidate)
            .build());
    line.attachRequirementProfile(candidate);
    return candidate;
  }

  @Transactional(readOnly = true)
  public RequirementProfileSnapshot historyVersion(UUID profileId, int profileVersion) {
    return versions
        .findByTenantIdAndProfileIdAndProfileVersion(
            TenantContext.requireTenantId(), profileId, profileVersion)
        .map(RequirementProfileVersion::getSnapshot)
        .orElseThrow(
            () ->
                new OrderDomainException(
                    "Requirement profile version not found: " + profileId + "/" + profileVersion,
                    404));
  }

  private JsonNode pinnedSource(RequirementProfileBasis basis) {
    if (basis.kind() != RequirementProfileBasis.Kind.SPEC_VERSION) {
      return null;
    }
    if (!"YARN_ARTICLE".equalsIgnoreCase(basis.specificationKind())) {
      throw new OrderDomainException(
          "Unsupported specification kind for requirement profile: " + basis.specificationKind());
    }
    YarnArticleSpecHistoryPort.Snapshot source =
        yarnHistory.historyVersion(basis.referenceId(), basis.specificationVersion());
    if (!basis.referenceId().equals(source.articleId())
        || basis.specificationVersion() != source.specificationVersion()) {
      throw new OrderDomainException("Pinned yarn specification history identity is inconsistent");
    }
    if (!source.specification().hasNonNull("productId")
        || !basis
            .productId()
            .toString()
            .equals(source.specification().path("productId").asText())) {
      throw new OrderDomainException(
          "Pinned yarn specification does not belong to the line product");
    }
    return source.specification().deepCopy();
  }

  private boolean hasResidualRequirementSpecs(Map<String, Object> moduleSpecs) {
    return moduleSpecs != null
        && moduleSpecs.keySet().stream().anyMatch(LEGACY_REQUIREMENT_KEYS::contains);
  }

  /** Missing facets preserve their previous values; UNSPECIFIED is the explicit clear operation. */
  private RequirementProfileInput mergeWithCurrent(
      SalesOrderLine line, RequirementProfileInput requested) {
    RequirementProfileSnapshot current = line.getRequirementProfileSnapshot();
    if (current == null) {
      return requested;
    }
    if (!current.basis().equals(requested.basis())) {
      // A new basis is a full re-resolution. Carrying old facets or constraints across would make
      // the new profile impossible to reproduce from its pinned source and recorded deviations.
      return requested;
    }

    Set<String> scope = new LinkedHashSet<>(current.scope());
    scope.addAll(requested.scope());

    Map<String, RequirementFacet> facets =
        current.facets().stream()
            .collect(
                Collectors.toMap(
                    RequirementFacet::identity,
                    Function.identity(),
                    (left, right) -> right,
                    LinkedHashMap::new));
    requested.facets().forEach(facet -> facets.put(facet.identity(), facet));

    Map<String, UnmodelledSpecConstraint> constraints =
        current.unmodelledConstraints().stream()
            .collect(
                Collectors.toMap(
                    UnmodelledSpecConstraint::field,
                    Function.identity(),
                    (left, right) -> right,
                    LinkedHashMap::new));
    requested.unmodelledConstraints().forEach(row -> constraints.put(row.field(), row));

    List<RequirementDeviation> deviations =
        requested.deviations().isEmpty()
            ? current.deviations().stream()
                .filter(
                    deviation ->
                        requested.facets().stream()
                            .noneMatch(
                                facet ->
                                    facet.state() == RequirementFacet.State.UNSPECIFIED
                                        && deviationMatches(deviation.facetIdentity(), facet)))
                .toList()
            : requested.deviations();
    return new RequirementProfileInput(
        requested.basis(),
        requested.scopeVersion(),
        requested.resolutionRuleVersion(),
        scope,
        List.copyOf(facets.values()),
        List.copyOf(constraints.values()),
        deviations);
  }

  private void validateDeviations(RequirementProfileInput input) {
    for (RequirementDeviation deviation : input.deviations()) {
      List<RequirementFacet> matches =
          input.facets().stream()
              .filter(facet -> deviationMatches(deviation.facetIdentity(), facet))
              .toList();
      if (matches.size() != 1) {
        throw new OrderDomainException(
            "Requirement deviation must identify exactly one effective facet: "
                + deviation.facetIdentity());
      }
      RequirementFacet facet = matches.getFirst();
      if (facet.state() == RequirementFacet.State.UNSPECIFIED || facet.decisionBasis() == null) {
        throw new OrderDomainException(
            "Requirement deviation must supply a bounded or unconstrained effective value: "
                + deviation.facetIdentity());
      }
      RequirementFacet.DecisionBasis basis = facet.decisionBasis();
      if (!deviation.reference().equals(basis.reference())
          || !deviation.actorId().equals(basis.actorId())
          || !deviation.decidedAt().equals(basis.decidedAt())) {
        throw new OrderDomainException(
            "Requirement deviation provenance disagrees with its effective facet: "
                + deviation.facetIdentity());
      }
    }
  }

  private boolean deviationMatches(String identity, RequirementFacet facet) {
    return identity.contains(":")
        ? identity.equals(facet.identity())
        : identity.equals(facet.kind().name());
  }

  private RequirementProfileInput applyServerScope(
      SalesOrderLine line, RequirementProfileInput input) {
    Set<String> mandatory = new LinkedHashSet<>();
    String scopeVersion = input.scopeVersion();
    ModuleType effectiveModuleType = resolveModuleType(line);
    if (input.basis().kind() == RequirementProfileBasis.Kind.SPEC_VERSION
        && "YARN_ARTICLE".equalsIgnoreCase(input.basis().specificationKind())) {
      if (effectiveModuleType != ModuleType.YARN) {
        throw new OrderDomainException(
            "Yarn specification requirement profile requires a YARN product line");
      }
      mandatory.addAll(yarnScope());
      scopeVersion = "YARN_ARTICLE_REQUIREMENTS_V1";
    } else if (effectiveModuleType != null) {
      switch (effectiveModuleType) {
        case FABRIC -> {
          mandatory.addAll(
              Set.of(
                  "WIDTH",
                  "WEIGHT",
                  "COLOUR_IDENTITY",
                  "SHADE_APPROVAL",
                  "CERTIFICATION",
                  "ORIGIN"));
          scopeVersion = "FABRIC_LINE_EXPLICIT_V1";
        }
        case YARN -> {
          mandatory.addAll(yarnScope());
          scopeVersion = "YARN_LINE_EXPLICIT_V1";
        }
        case FIBER -> {
          mandatory.addAll(Set.of("FIBRE_GRADE", "FIBRE_SHADE", "CERTIFICATION", "ORIGIN"));
          scopeVersion = "FIBRE_LINE_EXPLICIT_V1";
        }
        case DYE_FINISHING -> {
          mandatory.addAll(Set.of("COLOUR_IDENTITY", "SHADE_APPROVAL"));
          scopeVersion = "DYE_FINISHING_LINE_EXPLICIT_V1";
        }
      }
    } else {
      // A profile cannot claim completeness while the server cannot determine which real-world
      // product/process facets apply to the line. This identity deliberately has no facet value;
      // resolving the product or explicitly selecting the service module removes it in a new
      // immutable profile version.
      mandatory.add("PROFILE_SCOPE");
      scopeVersion = "UNRESOLVED_PROFILE_SCOPE_V1";
    }
    mandatory.addAll(input.scope());
    input.deviations().stream().map(RequirementDeviation::facetIdentity).forEach(mandatory::add);
    return new RequirementProfileInput(
        input.basis(),
        scopeVersion,
        "SALES_REQ_1_RESOLUTION_V1",
        mandatory,
        input.facets(),
        input.unmodelledConstraints(),
        input.deviations());
  }

  private ModuleType resolveModuleType(SalesOrderLine line) {
    ModuleType declared = line.getModuleType();
    if (line.getProductId() == null) {
      return declared;
    }

    ProductEvidenceQueryService.Reference product =
        products.findReferences(Set.of(line.getProductId())).stream()
            .filter(reference -> reference.id().equals(line.getProductId()) && reference.active())
            .findFirst()
            .orElseThrow(
                () ->
                    new OrderDomainException(
                        "Requirement profile needs an active product reference for the line"));

    // DYE_FINISHING is a process/service decision applied to a fabric product, so it remains an
    // explicit line classification. Product-owned FIBER/YARN/FABRIC scopes are server-derived.
    if (declared == ModuleType.DYE_FINISHING) {
      if (product.productType() != ProductType.FABRIC) {
        throw new OrderDomainException(
            "DYE_FINISHING requirement profile requires a FABRIC product");
      }
      return declared;
    }

    ModuleType derived =
        switch (product.productType()) {
          case FIBER -> ModuleType.FIBER;
          case YARN -> ModuleType.YARN;
          case FABRIC -> ModuleType.FABRIC;
          case CHEMICAL, CONSUMABLE -> null;
        };
    if (derived == null) {
      throw new OrderDomainException(
          "Typed requirement profiles are not defined for product type " + product.productType());
    }
    if (declared != null && declared != derived) {
      throw new OrderDomainException(
          "Line module type " + declared + " conflicts with product type " + product.productType());
    }
    return derived;
  }

  private Set<String> yarnScope() {
    return Set.of(
        "YARN_COUNT:RESULTANT", "YARN_TWIST", "YARN_CONSTRUCTION", "CERTIFICATION", "ORIGIN");
  }

  private RequirementProfileInput withPinnedYarnConstraints(
      RequirementProfileInput input, JsonNode pinnedSource) {
    if (pinnedSource == null
        || input.basis().kind() != RequirementProfileBasis.Kind.SPEC_VERSION
        || !"YARN_ARTICLE".equalsIgnoreCase(input.basis().specificationKind())) {
      return input;
    }

    Map<String, UnmodelledSpecConstraint> constraints =
        input.unmodelledConstraints().stream()
            .collect(
                Collectors.toMap(
                    UnmodelledSpecConstraint::field,
                    Function.identity(),
                    (left, right) -> right,
                    LinkedHashMap::new));
    String materialForm = pinnedSource.path("materialForm").asText(null);
    putPinnedConstraint(
        constraints, pinnedSource, "composition", "declared fibre composition", null, false);
    putPinnedConstraint(
        constraints,
        pinnedSource,
        "filamentCount",
        "filament count",
        "count",
        "STAPLE_SPUN".equals(materialForm));
    putPinnedConstraint(
        constraints,
        pinnedSource,
        "filamentForm",
        "continuous-filament form",
        "FilamentForm",
        "STAPLE_SPUN".equals(materialForm));
    putPinnedConstraint(
        constraints,
        pinnedSource,
        "twistContractionPercent",
        "twist contraction",
        "percent",
        false);
    putPinnedConstraint(
        constraints, pinnedSource, "materialForm", "yarn material form", "YarnMaterialForm", false);
    boolean componentsNotApplicable =
        "SINGLE".equals(pinnedSource.path("structureType").asText(null))
            && pinnedSource.path("structureComponents").isArray()
            && pinnedSource.path("structureComponents").isEmpty();
    putPinnedConstraint(
        constraints,
        pinnedSource,
        "structureComponents",
        "component structure",
        "YarnArticleStructureComponent",
        componentsNotApplicable);
    return new RequirementProfileInput(
        input.basis(),
        input.scopeVersion(),
        input.resolutionRuleVersion(),
        input.scope(),
        input.facets(),
        List.copyOf(constraints.values()),
        input.deviations());
  }

  private void putPinnedConstraint(
      Map<String, UnmodelledSpecConstraint> constraints,
      JsonNode source,
      String field,
      String meaning,
      String unitOrVocabulary,
      boolean notApplicable) {
    if (notApplicable) {
      constraints.put(
          field,
          new UnmodelledSpecConstraint(
              field,
              UnmodelledSpecConstraint.Status.NOT_APPLICABLE,
              null,
              unitOrVocabulary,
              meaning,
              "Pinned yarn structure marks this field not applicable"));
      return;
    }
    JsonNode value = source.get(field);
    boolean missing =
        value == null
            || value.isNull()
            || (value.isArray() && value.isEmpty())
            || (value.isTextual() && value.asText().isBlank());
    constraints.put(
        field,
        new UnmodelledSpecConstraint(
            field,
            missing
                ? UnmodelledSpecConstraint.Status.MISSING_SOURCE_VALUE
                : UnmodelledSpecConstraint.Status.RESOLVED_UNSUPPORTED,
            missing ? null : value.deepCopy(),
            unitOrVocabulary,
            missing ? null : meaning,
            missing
                ? "Pinned yarn specification does not contain a value"
                : "Pinned value retained; lot comparator is not implemented"));
  }

  private RequirementProfileInput withPinnedYarnNominals(
      RequirementProfileInput input, JsonNode pinnedSource) {
    if (pinnedSource == null
        || input.basis().kind() != RequirementProfileBasis.Kind.SPEC_VERSION
        || !"YARN_ARTICLE".equalsIgnoreCase(input.basis().specificationKind())) {
      return input;
    }

    List<RequirementFacet> facets = new java.util.ArrayList<>(input.facets());
    replaceNominal(
        facets, RequirementFacet.Kind.YARN_COUNT, "RESULTANT", yarnCountNominal(pinnedSource));
    replaceNominal(facets, RequirementFacet.Kind.YARN_TWIST, null, yarnTwistNominal(pinnedSource));
    replaceNominal(
        facets,
        RequirementFacet.Kind.YARN_CONSTRUCTION,
        null,
        yarnConstructionNominal(pinnedSource));
    return new RequirementProfileInput(
        input.basis(),
        input.scopeVersion(),
        input.resolutionRuleVersion(),
        input.scope(),
        facets,
        input.unmodelledConstraints(),
        input.deviations());
  }

  private void replaceNominal(
      List<RequirementFacet> facets,
      RequirementFacet.Kind kind,
      String nominalQualifier,
      RequirementFacetValue nominal) {
    String storedQualifier = nominalQualifier == null ? "SPEC_NOMINAL" : nominalQualifier;
    List<Integer> effectiveIndexes =
        java.util.stream.IntStream.range(0, facets.size())
            .filter(index -> facets.get(index).kind() == kind)
            .filter(
                index ->
                    nominalQualifier == null
                        ? !"SPEC_NOMINAL".equalsIgnoreCase(facets.get(index).qualifier())
                        : nominalQualifier.equalsIgnoreCase(facets.get(index).qualifier()))
            .boxed()
            .toList();
    if (effectiveIndexes.size() > 1) {
      throw new OrderDomainException(
          "Pinned specification nominal is ambiguous across requirement facets: " + kind);
    }
    Integer targetIndex =
        effectiveIndexes.size() == 1
            ? effectiveIndexes.getFirst()
            : java.util.stream.IntStream.range(0, facets.size())
                .filter(index -> facets.get(index).kind() == kind)
                .filter(index -> storedQualifier.equalsIgnoreCase(facets.get(index).qualifier()))
                .boxed()
                .findFirst()
                .orElse(null);
    if (targetIndex != null) {
      RequirementFacet current = facets.get(targetIndex);
      if (current.nominalValue() != null && !current.nominalValue().equals(nominal)) {
        throw new OrderDomainException(
            "Requirement nominal value disagrees with pinned specification: " + kind);
      }
      facets.set(
          targetIndex,
          new RequirementFacet(
              current.kind(),
              current.qualifier(),
              current.state(),
              current.comparison(),
              nominal,
              current.value(),
              current.decisionBasis()));
      if (nominalQualifier == null && effectiveIndexes.size() == 1) {
        RequirementFacet effective = facets.get(targetIndex);
        facets.removeIf(
            facet ->
                facet != effective
                    && facet.kind() == kind
                    && "SPEC_NOMINAL".equalsIgnoreCase(facet.qualifier()));
      }
      return;
    }
    facets.add(
        new RequirementFacet(
            kind,
            storedQualifier,
            RequirementFacet.State.UNSPECIFIED,
            RequirementFacet.Comparison.NONE,
            nominal,
            null,
            null));
  }

  private RequirementFacetValue.YarnCount yarnCountNominal(JsonNode source) {
    return new RequirementFacetValue.YarnCount(
        text(source, "originalCountSystem"),
        decimal(source, "originalCountValue"),
        text(source, "countBasis"),
        null,
        decimal(source, "resultantLinearDensityTex"));
  }

  private RequirementFacetValue.YarnTwist yarnTwistNominal(JsonNode source) {
    List<RequirementFacetValue.TwistStage> stages =
        java.util.stream.StreamSupport.stream(source.path("twistStages").spliterator(), false)
            .map(
                stage ->
                    new RequirementFacetValue.TwistStage(
                        text(stage, "stageType"),
                        subValue(text(stage, "direction")),
                        nominalNumeric(decimal(stage, "turnsPerMeter"), "TPM"),
                        stage.hasNonNull("sequence") ? stage.path("sequence").asInt() : null))
            .toList();
    return new RequirementFacetValue.YarnTwist(stages);
  }

  private RequirementFacetValue.YarnConstruction yarnConstructionNominal(JsonNode source) {
    JsonNode spinningSystem = source.path("spinningSystemRef");
    Set<String> features =
        java.util.stream.StreamSupport.stream(
                source.path("constructionFeatures").spliterator(), false)
            .filter(JsonNode::isTextual)
            .map(JsonNode::asText)
            .collect(Collectors.toUnmodifiableSet());
    boolean featuresKnown =
        source.has("constructionFeatures") && source.path("constructionFeatures").isArray();
    return new RequirementFacetValue.YarnConstruction(
        subValue(text(source, "structureType")),
        subValue(source.hasNonNull("foldCount") ? source.path("foldCount").asInt() : null),
        subValue(spinningSystem(source)),
        subValue(featuresKnown ? features : null));
  }

  private RequirementFacetValue.SpinningSystem spinningSystem(JsonNode source) {
    JsonNode ref = source.path("spinningSystemRef");
    UUID id = uuid(ref, "id");
    String code = text(ref, "code");
    String family = text(source, "spinningTechnologyFamily");
    // A partial historical catalogue reference remains explicitly unspecified. It must not be
    // repaired by guessing an identity or family from display text.
    if (id == null || code == null || family == null) return null;
    return new RequirementFacetValue.SpinningSystem(id, code, family);
  }

  private <T> RequirementFacetValue.SubValue<T> subValue(T value) {
    return new RequirementFacetValue.SubValue<>(
        value == null ? RequirementFacet.State.UNSPECIFIED : RequirementFacet.State.BOUNDED, value);
  }

  private RequirementFacetValue.SubValue<RequirementFacetValue.NumericBounds> nominalNumeric(
      BigDecimal value, String unit) {
    return subValue(
        value == null
            ? null
            : new RequirementFacetValue.NumericBounds(
                RequirementFacetValue.BoundType.EXACT, value, null, null, true, true, unit));
  }

  private String text(JsonNode source, String field) {
    return source != null && source.hasNonNull(field) ? source.path(field).asText() : null;
  }

  private BigDecimal decimal(JsonNode source, String field) {
    String value = text(source, field);
    return value == null || value.isBlank() ? null : new BigDecimal(value);
  }

  private UUID uuid(JsonNode source, String field) {
    String value = text(source, field);
    return value == null || value.isBlank() ? null : UUID.fromString(value);
  }
}
