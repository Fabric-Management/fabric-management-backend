package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.app.ProductEvidenceQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.sales.salesorder.app.RequirementProfileResolver.LineContext;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.port.YarnArticleSpecHistoryPort;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileBasis;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.domain.requirement.UnmodelledSpecConstraint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The pure profile resolution behind the safe edit (CEDIT-02 §2.5, scenario §16): a partial input
 * resolves against the base's pinned profile, a new basis resolves from scratch, the pinned source
 * is read by its fixed version, the fingerprint is semantic, and nothing is written. The ports are
 * mocked as in {@link RequirementProfileServiceTest}; the valid examples are taken from there and
 * from the facet value records.
 */
@ExtendWith(MockitoExtension.class)
class RequirementProfileResolverTest {

  private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
  private static final UUID PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final Instant DECIDED_AT = Instant.parse("2026-09-18T10:00:00Z");

  private static final RequirementProfileBasis BASIS =
      new RequirementProfileBasis(
          RequirementProfileBasis.Kind.LINE_EXPLICIT,
          null,
          null,
          null,
          null,
          ACTOR,
          DECIDED_AT,
          "line-explicit");

  private static final RequirementFacet.DecisionBasis DECISION =
      new RequirementFacet.DecisionBasis(
          RequirementFacet.DecisionBasis.Source.CUSTOMER_INSTRUCTION,
          "customer-contract",
          ACTOR,
          DECIDED_AT);

  @Mock private YarnArticleSpecHistoryPort yarnHistory;
  @Mock private ProductEvidenceQueryService products;

  private RequirementProfileResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new RequirementProfileResolver(yarnHistory, products);
  }

  // ── resolution against the base ────────────────────────────────────────────

  @Test
  @DisplayName("S16.5: a partial input resolved against the base profile keeps the base facets")
  void partialInputKeepsBaseFacets() {
    RequirementProfileSnapshot base =
        resolver.resolve(
            fabricLine(null, null, null), input(BASIS, width("150"), weight("180")), Map.of());
    LineContext line = fabricLine(base, PROFILE_ID, 2);

    RequirementProfileSnapshot resolved =
        resolver.resolve(line, input(BASIS, weight("200")), Map.of());

    assertThat(resolved.facets())
        .extracting(RequirementFacet::identity)
        .containsExactlyInAnyOrder("WIDTH:FINISHED", "WEIGHT:FINISHED");
    assertThat(facet(resolved, "WIDTH:FINISHED")).isEqualTo(facet(base, "WIDTH:FINISHED"));
    assertThat(facet(resolved, "WEIGHT:FINISHED").value()).isEqualTo(weight("200").value());
    assertThat(resolved.basis()).isEqualTo(BASIS);
    assertThat(resolved.profileId()).isEqualTo(PROFILE_ID);
    assertThat(resolved.profileVersion()).isEqualTo(3);
    assertThat(resolved.fingerprint()).isNotEqualTo(base.fingerprint());
    verifyNoInteractions(yarnHistory, products);
  }

  @Test
  @DisplayName("S16.6: an input with a new basis resolves from scratch, without the base facets")
  void newBasisResolvesFromScratch() {
    RequirementProfileSnapshot base =
        resolver.resolve(
            fabricLine(null, null, null), input(BASIS, width("150"), weight("180")), Map.of());
    RequirementProfileBasis newBasis =
        new RequirementProfileBasis(
            RequirementProfileBasis.Kind.LINE_EXPLICIT,
            null,
            null,
            null,
            null,
            ACTOR,
            DECIDED_AT.plusSeconds(60),
            "line-explicit-revised");

    RequirementProfileSnapshot resolved =
        resolver.resolve(fabricLine(base, PROFILE_ID, 2), input(newBasis, weight("200")), Map.of());

    assertThat(resolved.basis()).isEqualTo(newBasis);
    assertThat(resolved.facets())
        .extracting(RequirementFacet::identity)
        .containsExactly("WEIGHT:FINISHED");
    assertThat(resolved.profileId()).isEqualTo(PROFILE_ID);
    assertThat(resolved.profileVersion()).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "S16.1: a pinned SPEC_VERSION source is read by its fixed version; re-resolving the base's"
          + " own basis gives the base fingerprint")
  void pinnedSourceVersionIsKept() {
    UUID articleId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    when(products.findReferences(Set.of(productId)))
        .thenReturn(
            List.of(
                new ProductEvidenceQueryService.Reference(
                    productId, ProductType.YARN, 1L, Instant.EPOCH, true)));
    ObjectNode source = yarnSource(productId);
    when(yarnHistory.historyVersion(articleId, 3))
        .thenReturn(
            new YarnArticleSpecHistoryPort.Snapshot(
                articleId, 3, source, ACTOR, Instant.parse("2026-09-18T09:00:00Z")));
    RequirementProfileBasis pinned =
        new RequirementProfileBasis(
            RequirementProfileBasis.Kind.SPEC_VERSION,
            productId,
            "YARN_ARTICLE",
            articleId,
            3,
            ACTOR,
            DECIDED_AT,
            "order-line");
    RequirementProfileInput specInput =
        new RequirementProfileInput(
            pinned, "yarn-v1", "sales-req-v1", Set.of(), List.of(), List.of(), List.of());

    RequirementProfileSnapshot base =
        resolver.resolve(new LineContext(productId, null, null, null, null), specInput, Map.of());
    RequirementProfileSnapshot again =
        resolver.resolve(
            new LineContext(productId, null, base, PROFILE_ID, 2), specInput, Map.of());

    assertThat(base.pinnedSource()).isEqualTo(source);
    assertThat(again.pinnedSource()).isEqualTo(source);
    assertThat(again.basis().specificationVersion()).isEqualTo(3);
    assertThat(again.fingerprint()).isEqualTo(base.fingerprint());
    assertThat(again.profileId()).isEqualTo(PROFILE_ID);
    assertThat(again.profileVersion()).isEqualTo(3);
    verify(yarnHistory, times(2)).historyVersion(articleId, 3);
    verifyNoMoreInteractions(yarnHistory);
  }

  @Test
  @DisplayName(
      "S16.4: the fingerprint ignores facet order, letter case of scope and qualifier, and the"
          + " profile's identity and version")
  void fingerprintIsSemantic() {
    RequirementProfileInput written =
        new RequirementProfileInput(
            BASIS,
            "fabric-v1",
            "sales-req-v1",
            Set.of("width:finished"),
            List.of(
                facet(RequirementFacet.Kind.WIDTH, "finished", widthValue("150")),
                facet(RequirementFacet.Kind.WEIGHT, "finished", weightValue("180")),
                certification(" gots ", "organic")),
            List.of(),
            List.of());
    RequirementProfileInput rewritten =
        new RequirementProfileInput(
            BASIS,
            "fabric-v1",
            "sales-req-v1",
            Set.of("WIDTH:FINISHED"),
            List.of(
                certification("GOTS", "ORGANIC"),
                facet(RequirementFacet.Kind.WEIGHT, "FINISHED", weightValue("180")),
                facet(RequirementFacet.Kind.WIDTH, "FINISHED", widthValue("150"))),
            List.of(),
            List.of());

    RequirementProfileSnapshot first =
        resolver.resolve(fabricLine(null, UUID.randomUUID(), 4), written, Map.of());
    RequirementProfileSnapshot second =
        resolver.resolve(fabricLine(null, UUID.randomUUID(), 7), rewritten, Map.of());
    RequirementProfileSnapshot different =
        resolver.resolve(
            fabricLine(null, null, null), input(BASIS, width("151"), weight("180")), Map.of());

    assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
    assertThat(second.profileId()).isNotEqualTo(first.profileId());
    assertThat(second.profileVersion()).isNotEqualTo(first.profileVersion());
    assertThat(different.fingerprint()).isNotEqualTo(first.fingerprint());
  }

  @Test
  @DisplayName(
      "S16.1, S16.3: resolution writes nothing; the line context and base profile are unchanged")
  void resolutionWritesNothing() {
    // Only the two read ports are injected: no repository, no profile-version store.
    assertThat(
            Arrays.stream(RequirementProfileResolver.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType))
        .containsExactlyInAnyOrder(
            YarnArticleSpecHistoryPort.class, ProductEvidenceQueryService.class);
    RequirementProfileSnapshot base =
        resolver.resolve(
            fabricLine(null, null, null), input(BASIS, width("150"), weight("180")), Map.of());
    String baseFingerprint = base.fingerprint();
    List<RequirementFacet> baseFacets = List.copyOf(base.facets());
    LineContext line = fabricLine(base, PROFILE_ID, 2);
    LineContext copy = fabricLine(base, PROFILE_ID, 2);

    RequirementProfileSnapshot resolved =
        resolver.resolve(line, input(BASIS, weight("200")), Map.of());

    assertThat(line).isEqualTo(copy);
    assertThat(line.currentProfile()).isSameAs(base);
    assertThat(base.fingerprint()).isEqualTo(baseFingerprint);
    assertThat(base.facets()).isEqualTo(baseFacets);
    assertThat(base.profileVersion()).isEqualTo(1);
    assertThat(resolved).isNotSameAs(base);
    verifyNoInteractions(yarnHistory, products);
  }

  @Test
  @DisplayName("S15.18: a free sourceValue of an unmodelled constraint is kept verbatim")
  void freeSourceValueIsKeptVerbatim() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String raw = "{\"anyKey\":[1,{\"nested\":true}],\"x\":null}";
    JsonNode free = mapper.readTree(raw);
    UnmodelledSpecConstraint constraint =
        new UnmodelledSpecConstraint(
            "blendTolerance",
            UnmodelledSpecConstraint.Status.RESOLVED_UNSUPPORTED,
            free,
            null,
            "declared blend tolerance",
            "No comparator for blend tolerance yet");
    RequirementProfileInput withConstraint =
        new RequirementProfileInput(
            BASIS,
            "fabric-v1",
            "sales-req-v1",
            Set.of(),
            List.of(width("150")),
            List.of(constraint),
            List.of());

    RequirementProfileSnapshot base =
        resolver.resolve(fabricLine(null, null, null), withConstraint, Map.of());
    RequirementProfileSnapshot partial =
        resolver.resolve(fabricLine(base, PROFILE_ID, 2), input(BASIS, weight("180")), Map.of());

    for (RequirementProfileSnapshot profile : List.of(base, partial)) {
      assertThat(profile.unmodelledConstraints()).hasSize(1);
      UnmodelledSpecConstraint kept = profile.unmodelledConstraints().getFirst();
      assertThat(kept.field()).isEqualTo("blendTolerance");
      assertThat(kept.sourceValue()).isEqualTo(free);
      assertThat(mapper.writeValueAsString(kept.sourceValue())).isEqualTo(raw);
      assertThat(profile.incompleteReasons()).doesNotContain("UNRESOLVED_SPEC:blendTolerance");
    }
  }

  // ── every valueType resolves ───────────────────────────────────────────────

  @Nested
  class EveryValueType {

    @Test
    @DisplayName("S15.16: a CERTIFICATION facet resolves")
    void certification() {
      assertResolves(
          ModuleType.FABRIC, RequirementProfileResolverTest.certification("GOTS", "ORGANIC"));
    }

    @Test
    @DisplayName("S15.16: an ORIGIN facet resolves")
    void origin() {
      assertResolves(
          ModuleType.FABRIC,
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
              DECISION));
    }

    @Test
    @DisplayName("S15.16: a COLOUR_IDENTITY facet resolves")
    void colourIdentity() {
      assertResolves(
          ModuleType.FABRIC,
          facet(
              RequirementFacet.Kind.COLOUR_IDENTITY,
              null,
              new RequirementFacetValue.ColourIdentity(UUID.randomUUID())));
    }

    @Test
    @DisplayName("S15.16: a SHADE_APPROVAL facet resolves")
    void shadeApproval() {
      assertResolves(
          ModuleType.FABRIC,
          facet(
              RequirementFacet.Kind.SHADE_APPROVAL,
              null,
              new RequirementFacetValue.ShadeApproval(
                  true, RequirementFacetValue.ApprovalKind.LAB_DIP)));
    }

    @Test
    @DisplayName("S15.16: a WIDTH facet resolves")
    void width() {
      assertResolves(ModuleType.FABRIC, RequirementProfileResolverTest.width("150"));
    }

    @Test
    @DisplayName("S15.16: a WEIGHT facet resolves")
    void weight() {
      assertResolves(ModuleType.FABRIC, RequirementProfileResolverTest.weight("180"));
    }

    @Test
    @DisplayName("S15.16: a YARN_COUNT facet resolves")
    void yarnCount() {
      assertResolves(
          ModuleType.YARN,
          facet(
              RequirementFacet.Kind.YARN_COUNT,
              "resultant",
              new RequirementFacetValue.YarnCount(
                  "NE",
                  new BigDecimal("30"),
                  "RESULTANT",
                  "SINGLE",
                  new BigDecimal("19.68"),
                  exact("19.68", "tex"))));
    }

    @Test
    @DisplayName("S15.16: a YARN_TWIST facet resolves")
    void yarnTwist() {
      assertResolves(
          ModuleType.YARN,
          facet(
              RequirementFacet.Kind.YARN_TWIST,
              "single-direction",
              new RequirementFacetValue.YarnTwist(
                  List.of(
                      new RequirementFacetValue.TwistStage(
                          "SINGLE",
                          new RequirementFacetValue.SubValue<>(RequirementFacet.State.BOUNDED, "Z"),
                          new RequirementFacetValue.SubValue<>(
                              RequirementFacet.State.UNSPECIFIED, null),
                          1)))));
    }

    @Test
    @DisplayName("S15.16: a YARN_CONSTRUCTION facet resolves")
    void yarnConstruction() {
      assertResolves(
          ModuleType.YARN,
          facet(
              RequirementFacet.Kind.YARN_CONSTRUCTION,
              null,
              new RequirementFacetValue.YarnConstruction(
                  new RequirementFacetValue.SubValue<>(RequirementFacet.State.BOUNDED, "SINGLE"),
                  new RequirementFacetValue.SubValue<>(RequirementFacet.State.UNSPECIFIED, null),
                  new RequirementFacetValue.SubValue<>(RequirementFacet.State.UNSPECIFIED, null),
                  new RequirementFacetValue.SubValue<>(
                      RequirementFacet.State.BOUNDED, Set.of("COMPACT")))));
    }

    @Test
    @DisplayName("S15.16: a CATEGORICAL facet (fibre grade) resolves")
    void categorical() {
      assertResolves(
          ModuleType.FIBER,
          facet(
              RequirementFacet.Kind.FIBRE_GRADE,
              null,
              new RequirementFacetValue.Categorical(Set.of("long_staple"))));
    }

    private void assertResolves(ModuleType moduleType, RequirementFacet facet) {
      RequirementProfileSnapshot profile =
          resolver.resolve(
              new LineContext(null, moduleType, null, null, null), input(BASIS, facet), Map.of());

      assertThat(profile.facets())
          .filteredOn(resolved -> resolved.identity().equals(facet.identity()))
          .singleElement()
          .satisfies(
              resolved -> {
                assertThat(resolved.state()).isEqualTo(RequirementFacet.State.BOUNDED);
                assertThat(resolved.comparison()).isEqualTo(facet.comparison());
                assertThat(resolved.value()).isEqualTo(facet.value());
                assertThat(resolved.decisionBasis()).isEqualTo(DECISION);
              });
      assertThat(profile.fingerprint()).matches("[0-9a-f]{64}");
      verifyNoInteractions(yarnHistory, products);
    }
  }

  // ── fixtures ───────────────────────────────────────────────────────────────

  private static LineContext fabricLine(
      RequirementProfileSnapshot profile, UUID profileId, Integer profileVersion) {
    return new LineContext(null, ModuleType.FABRIC, profile, profileId, profileVersion);
  }

  private static RequirementProfileInput input(
      RequirementProfileBasis basis, RequirementFacet... facets) {
    return new RequirementProfileInput(
        basis, "fabric-v1", "sales-req-v1", Set.of(), List.of(facets), List.of(), List.of());
  }

  private static RequirementFacet facet(
      RequirementFacet.Kind kind, String qualifier, RequirementFacetValue value) {
    RequirementFacet.Comparison comparison =
        switch (kind) {
          case CERTIFICATION -> RequirementFacet.Comparison.ALL;
          case ORIGIN -> RequirementFacet.Comparison.SET_MEMBERSHIP;
          case WIDTH -> RequirementFacet.Comparison.MINIMUM;
          default -> RequirementFacet.Comparison.EXACT;
        };
    return new RequirementFacet(
        kind, qualifier, RequirementFacet.State.BOUNDED, comparison, value, DECISION);
  }

  private static RequirementFacet width(String minimum) {
    return facet(RequirementFacet.Kind.WIDTH, "finished", widthValue(minimum));
  }

  private static RequirementFacet weight(String exact) {
    return facet(RequirementFacet.Kind.WEIGHT, "finished", weightValue(exact));
  }

  private static RequirementFacet certification(String scheme, String kind) {
    return facet(
        RequirementFacet.Kind.CERTIFICATION,
        null,
        new RequirementFacetValue.Certification(
            List.of(new RequirementFacetValue.CertificateRef(scheme, kind))));
  }

  private static RequirementFacetValue.Width widthValue(String minimum) {
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

  private static RequirementFacetValue.Weight weightValue(String exact) {
    return new RequirementFacetValue.Weight(exact(exact, "g/m²"));
  }

  private static RequirementFacetValue.NumericBounds exact(String value, String unit) {
    return new RequirementFacetValue.NumericBounds(
        RequirementFacetValue.BoundType.EXACT, new BigDecimal(value), null, null, true, true, unit);
  }

  private static RequirementFacet facet(RequirementProfileSnapshot profile, String identity) {
    return profile.facets().stream()
        .filter(facet -> facet.identity().equals(identity))
        .findFirst()
        .orElseThrow();
  }

  /** The pinned yarn article source of RequirementProfileServiceTest. */
  private static ObjectNode yarnSource(UUID productId) {
    ObjectNode source =
        JsonNodeFactory.instance
            .objectNode()
            .put("productId", productId.toString())
            .put("articleSpecVersion", 3)
            .put("originalCountSystem", "TEX")
            .put("originalCountValue", "30")
            .put("countBasis", "RESULTANT")
            .put("resultantLinearDensityTex", "30")
            .put("structureType", "SINGLE")
            .put("materialForm", "STAPLE_SPUN")
            .put("composition", "100% CO");
    source.putArray("twistStages");
    source.putArray("constructionFeatures");
    source.putArray("structureComponents");
    return source;
  }
}
