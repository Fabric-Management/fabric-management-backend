package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.app.ProductEvidenceQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
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
import com.fabricmanagement.sales.salesorder.infra.repository.RequirementProfileVersionRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RequirementProfileServiceTest {

  @Mock private RequirementProfileVersionRepository versions;
  @Mock private YarnArticleSpecHistoryPort yarnHistory;
  @Mock private ProductEvidenceQueryService products;

  private RequirementProfileService service;
  private SalesOrderLine line;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    service = new RequirementProfileService(versions, yarnHistory, products);
    org.mockito.Mockito.lenient()
        .when(products.findReferences(any()))
        .thenAnswer(
            invocation -> {
              java.util.Collection<UUID> ids = invocation.getArgument(0);
              return ids.stream()
                  .map(
                      id ->
                          new ProductEvidenceQueryService.Reference(
                              id, ProductType.YARN, 1L, Instant.EPOCH, true))
                  .toList();
            });
    line =
        SalesOrderLine.builder()
            .productDesc("fabric")
            .requestedQty(java.math.BigDecimal.ONE)
            .unit("M")
            .build();
    ReflectionTestUtils.setField(line, "id", UUID.randomUUID());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void appendsEachChangedVersionAndKeepsVersionOneSnapshotUnchanged() {
    RequirementProfileSnapshot first = service.apply(line, explicit("150"), Map.of());
    RequirementProfileSnapshot second = service.apply(line, explicit("151"), Map.of());
    RequirementProfileSnapshot third = service.apply(line, explicit("152"), Map.of());

    ArgumentCaptor<RequirementProfileVersion> rows =
        ArgumentCaptor.forClass(RequirementProfileVersion.class);
    verify(versions, org.mockito.Mockito.times(3)).save(rows.capture());

    assertThat(rows.getAllValues())
        .extracting(RequirementProfileVersion::getProfileVersion)
        .containsExactly(1, 2, 3);
    assertThat(rows.getAllValues().get(0).getSnapshot()).isEqualTo(first);
    assertThat(rows.getAllValues().get(0).getSnapshot()).isNotEqualTo(second);
    assertThat(line.getRequirementProfileSnapshot()).isEqualTo(third);
  }

  @Test
  void identicalInputDoesNotCreateCounterOnlyVersion() {
    RequirementProfileInput input = explicit("150");
    service.apply(line, input, Map.of());
    service.apply(line, input, Map.of());

    verify(versions, org.mockito.Mockito.times(1)).save(any());
    assertThat(line.getRequirementProfileVersion()).isEqualTo(1);
  }

  @Test
  void serverOwnedFabricScopePreventsAWidthOnlyProfileFromClaimingCompleteness() {
    line.setModuleType(ModuleType.FABRIC);

    RequirementProfileSnapshot profile = service.apply(line, explicit("150"), Map.of());

    assertThat(profile.complete()).isFalse();
    assertThat(profile.scopeVersion()).isEqualTo("FABRIC_LINE_EXPLICIT_V1");
    assertThat(profile.resolutionRuleVersion()).isEqualTo("SALES_REQ_1_RESOLUTION_V1");
    assertThat(profile.incompleteReasons())
        .contains(
            "UNSPECIFIED:WEIGHT",
            "UNSPECIFIED:COLOUR_IDENTITY",
            "UNSPECIFIED:SHADE_APPROVAL",
            "UNSPECIFIED:CERTIFICATION",
            "UNSPECIFIED:ORIGIN");
  }

  @Test
  void serverDerivesFabricScopeFromTheActiveProductWhenModuleTypeIsOmitted() {
    UUID productId = UUID.randomUUID();
    line.setProductId(productId);
    line.setProductDesc(null);
    when(products.findReferences(Set.of(productId)))
        .thenReturn(
            List.of(
                new ProductEvidenceQueryService.Reference(
                    productId, ProductType.FABRIC, 4L, Instant.EPOCH, true)));

    RequirementProfileSnapshot profile = service.apply(line, explicit("150"), Map.of());

    assertThat(profile.scopeVersion()).isEqualTo("FABRIC_LINE_EXPLICIT_V1");
    assertThat(profile.complete()).isFalse();
    assertThat(profile.incompleteReasons()).contains("UNSPECIFIED:WEIGHT");
  }

  @Test
  void declaredModuleTypeCannotContradictTheActiveProduct() {
    UUID productId = UUID.randomUUID();
    line.setProductId(productId);
    line.setProductDesc(null);
    line.setModuleType(ModuleType.FIBER);
    when(products.findReferences(Set.of(productId)))
        .thenReturn(
            List.of(
                new ProductEvidenceQueryService.Reference(
                    productId, ProductType.FABRIC, 4L, Instant.EPOCH, true)));

    assertThatThrownBy(() -> service.apply(line, explicit("150"), Map.of()))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderDomainException.class)
        .hasMessageContaining("conflicts with product type FABRIC");
  }

  @Test
  void onlyResidualRequirementKeysMakeTheTypedProfileUntyped() {
    RequirementProfileSnapshot descriptive =
        service.apply(line, explicit("150"), Map.of("weaveType", "TWILL"));
    assertThat(descriptive.incompleteReasons()).doesNotContain("UNTYPED_REQUIREMENTS");

    RequirementProfileSnapshot legacyRequirement =
        service.apply(line, explicit("151"), Map.of("originReq", "TR"));
    assertThat(legacyRequirement.incompleteReasons()).contains("UNTYPED_REQUIREMENTS");
  }

  @Test
  void specVersionPinsTheTypedHistoryPortSnapshot() {
    UUID articleId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    line.setProductId(productId);
    line.setProductDesc(null);
    var source =
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
    when(yarnHistory.historyVersion(articleId, 3))
        .thenReturn(
            new YarnArticleSpecHistoryPort.Snapshot(
                articleId, 3, source, UUID.randomUUID(), Instant.parse("2026-09-18T09:00:00Z")));
    RequirementProfileInput input =
        new RequirementProfileInput(
            new RequirementProfileBasis(
                RequirementProfileBasis.Kind.SPEC_VERSION,
                productId,
                "YARN_ARTICLE",
                articleId,
                3,
                UUID.randomUUID(),
                Instant.parse("2026-09-18T10:00:00Z"),
                "order-line"),
            "yarn-v1",
            "sales-req-v1",
            Set.of(),
            List.of(),
            List.of(),
            List.of());

    RequirementProfileSnapshot result = service.apply(line, input, Map.of());

    assertThat(result.pinnedSource()).isEqualTo(source);
    assertThat(result.facets())
        .filteredOn(facet -> facet.kind() == RequirementFacet.Kind.YARN_COUNT)
        .singleElement()
        .satisfies(
            facet -> {
              assertThat(facet.state()).isEqualTo(RequirementFacet.State.UNSPECIFIED);
              assertThat(((RequirementFacetValue.YarnCount) facet.nominalValue()).originalSystem())
                  .isEqualTo("TEX");
            });
    RequirementFacet.DecisionBasis componentBasis =
        new RequirementFacet.DecisionBasis(
            RequirementFacet.DecisionBasis.Source.CUSTOMER_INSTRUCTION,
            "component-count-rule",
            UUID.randomUUID(),
            Instant.parse("2026-09-18T10:03:00Z"));
    RequirementFacet componentCount =
        new RequirementFacet(
            RequirementFacet.Kind.YARN_COUNT,
            "component-1",
            RequirementFacet.State.BOUNDED,
            RequirementFacet.Comparison.EXACT,
            new RequirementFacetValue.YarnCount(
                "TEX",
                new BigDecimal("15"),
                "COMPONENT",
                "COMPONENT_1",
                new BigDecimal("15"),
                new RequirementFacetValue.NumericBounds(
                    RequirementFacetValue.BoundType.EXACT,
                    new BigDecimal("15"),
                    null,
                    null,
                    true,
                    true,
                    "tex")),
            componentBasis);
    RequirementProfileInput componentOnly =
        new RequirementProfileInput(
            input.basis(),
            input.scopeVersion(),
            input.resolutionRuleVersion(),
            Set.of(),
            List.of(componentCount),
            List.of(),
            List.of(
                new RequirementDeviation(
                    componentCount.identity(),
                    componentBasis.reference(),
                    componentBasis.actorId(),
                    componentBasis.decidedAt())));
    SalesOrderLine componentLine =
        SalesOrderLine.builder()
            .productId(productId)
            .requestedQty(BigDecimal.ONE)
            .unit("KG")
            .build();
    ReflectionTestUtils.setField(componentLine, "id", UUID.randomUUID());

    RequirementProfileSnapshot componentProfile =
        service.apply(componentLine, componentOnly, Map.of());

    assertThat(componentProfile.facets())
        .filteredOn(facet -> facet.kind() == RequirementFacet.Kind.YARN_COUNT)
        .extracting(RequirementFacet::identity)
        .containsExactly("YARN_COUNT:COMPONENT-1", "YARN_COUNT:RESULTANT");
    assertThat(componentProfile.incompleteReasons()).contains("UNSPECIFIED:YARN_COUNT:RESULTANT");
    RequirementFacet.DecisionBasis ruleBasis =
        new RequirementFacet.DecisionBasis(
            RequirementFacet.DecisionBasis.Source.CUSTOMER_INSTRUCTION,
            "count-rule",
            UUID.randomUUID(),
            Instant.parse("2026-09-18T10:05:00Z"));
    RequirementFacet countRule =
        new RequirementFacet(
            RequirementFacet.Kind.YARN_COUNT,
            "resultant",
            RequirementFacet.State.BOUNDED,
            RequirementFacet.Comparison.EXACT,
            new RequirementFacetValue.YarnCount(
                "TEX",
                new BigDecimal("30"),
                "RESULTANT",
                "SINGLE",
                new BigDecimal("30"),
                new RequirementFacetValue.NumericBounds(
                    RequirementFacetValue.BoundType.EXACT,
                    new BigDecimal("30"),
                    null,
                    null,
                    true,
                    true,
                    "tex")),
            ruleBasis);
    RequirementProfileInput withRule =
        new RequirementProfileInput(
            input.basis(),
            input.scopeVersion(),
            input.resolutionRuleVersion(),
            Set.of(),
            List.of(countRule),
            List.of(),
            List.of());

    RequirementProfileSnapshot resolved = service.apply(line, withRule, Map.of());

    assertThat(resolved.facets())
        .filteredOn(facet -> facet.kind() == RequirementFacet.Kind.YARN_COUNT)
        .singleElement()
        .satisfies(
            facet -> {
              assertThat(facet.qualifier()).isEqualTo("RESULTANT");
              assertThat(facet.state()).isEqualTo(RequirementFacet.State.BOUNDED);
              assertThat(facet.nominalValue()).isNotNull();
            });
    assertThat(resolved.incompleteReasons()).doesNotContain("UNSPECIFIED:YARN_COUNT:RESULTANT");
    verify(yarnHistory, org.mockito.Mockito.times(3)).historyVersion(articleId, 3);
  }

  @Test
  void deviationOverridesPinnedNominalAndReproducesFromTheSameBasis() {
    UUID articleId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    Instant decidedAt = Instant.parse("2026-09-18T10:00:00Z");
    line.setProductId(productId);
    line.setProductDesc(null);
    var source =
        JsonNodeFactory.instance
            .objectNode()
            .put("productId", productId.toString())
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
    when(yarnHistory.historyVersion(articleId, 3))
        .thenReturn(
            new YarnArticleSpecHistoryPort.Snapshot(
                articleId, 3, source, actor, Instant.parse("2026-09-18T09:00:00Z")));
    RequirementFacet.DecisionBasis decision =
        new RequirementFacet.DecisionBasis(
            RequirementFacet.DecisionBasis.Source.CUSTOMER_INSTRUCTION,
            "customer-deviation-7",
            actor,
            decidedAt);
    RequirementFacet effectiveCount =
        new RequirementFacet(
            RequirementFacet.Kind.YARN_COUNT,
            "resultant",
            RequirementFacet.State.BOUNDED,
            RequirementFacet.Comparison.EXACT,
            new RequirementFacetValue.YarnCount(
                "TEX",
                new BigDecimal("32"),
                "RESULTANT",
                "SINGLE",
                new BigDecimal("32"),
                new RequirementFacetValue.NumericBounds(
                    RequirementFacetValue.BoundType.EXACT,
                    new BigDecimal("32"),
                    null,
                    null,
                    true,
                    true,
                    "tex")),
            decision);
    RequirementProfileInput input =
        new RequirementProfileInput(
            new RequirementProfileBasis(
                RequirementProfileBasis.Kind.SPEC_VERSION,
                productId,
                "YARN_ARTICLE",
                articleId,
                3,
                actor,
                decidedAt,
                "order-line"),
            "caller-value-is-ignored",
            "caller-value-is-ignored",
            Set.of(),
            List.of(effectiveCount),
            List.of(),
            List.of(
                new RequirementDeviation(
                    effectiveCount.identity(), "customer-deviation-7", actor, decidedAt)));

    RequirementProfileSnapshot first = service.apply(line, input, Map.of());
    SalesOrderLine reproducedLine =
        SalesOrderLine.builder()
            .productId(productId)
            .requestedQty(BigDecimal.ONE)
            .unit("KG")
            .build();
    ReflectionTestUtils.setField(reproducedLine, "id", UUID.randomUUID());
    RequirementProfileSnapshot reproduced = service.apply(reproducedLine, input, Map.of());

    RequirementFacet resolved =
        first.facets().stream()
            .filter(facet -> facet.kind() == RequirementFacet.Kind.YARN_COUNT)
            .findFirst()
            .orElseThrow();
    assertThat(((RequirementFacetValue.YarnCount) resolved.nominalValue()).resultantTex())
        .isEqualByComparingTo("30");
    assertThat(((RequirementFacetValue.YarnCount) resolved.value()).resultantTex())
        .isEqualByComparingTo("32");
    assertThat(reproduced.fingerprint()).isEqualTo(first.fingerprint());
    assertThat(reproduced.facets()).isEqualTo(first.facets());
  }

  @Test
  void deviationWithoutAnEffectiveFacetIsRejected() {
    RequirementProfileInput base = explicit("150");
    RequirementProfileInput invalid =
        new RequirementProfileInput(
            base.basis(),
            base.scopeVersion(),
            base.resolutionRuleVersion(),
            base.scope(),
            base.facets(),
            base.unmodelledConstraints(),
            List.of(
                new RequirementDeviation(
                    "WEIGHT:FINISHED",
                    "missing-override",
                    UUID.randomUUID(),
                    Instant.parse("2026-09-18T10:00:00Z"))));

    assertThatThrownBy(() -> service.apply(line, invalid, Map.of()))
        .hasMessageContaining("exactly one effective facet");
  }

  @Test
  void explicitUnspecifiedClearsTheFacetAndItsDeviationProvenance() {
    RequirementProfileInput base = explicit("150");
    RequirementFacet bounded = base.facets().getFirst();
    RequirementFacet.DecisionBasis decision = bounded.decisionBasis();
    RequirementProfileInput deviated =
        new RequirementProfileInput(
            base.basis(),
            base.scopeVersion(),
            base.resolutionRuleVersion(),
            base.scope(),
            base.facets(),
            base.unmodelledConstraints(),
            List.of(
                new RequirementDeviation(
                    bounded.identity(),
                    decision.reference(),
                    decision.actorId(),
                    decision.decidedAt())));
    service.apply(line, deviated, Map.of());
    RequirementFacet clearedFacet =
        new RequirementFacet(
            bounded.kind(),
            bounded.qualifier(),
            RequirementFacet.State.UNSPECIFIED,
            RequirementFacet.Comparison.NONE,
            null,
            null);
    RequirementProfileInput clear =
        new RequirementProfileInput(
            base.basis(),
            base.scopeVersion(),
            base.resolutionRuleVersion(),
            base.scope(),
            List.of(clearedFacet),
            List.of(),
            List.of());

    RequirementProfileSnapshot result = service.apply(line, clear, Map.of());

    assertThat(result.deviations()).isEmpty();
    assertThat(result.complete()).isFalse();
    assertThat(result.incompleteReasons()).contains("UNSPECIFIED:WIDTH:FINISHED");
  }

  @Test
  void readsHistoricalVersionByTenantProfileAndVersion() {
    RequirementProfileSnapshot snapshot = service.apply(line, explicit("150"), Map.of());
    RequirementProfileVersion row = org.mockito.Mockito.mock(RequirementProfileVersion.class);
    when(row.getSnapshot()).thenReturn(snapshot);
    UUID tenantId = TenantContext.requireTenantId();
    when(versions.findByTenantIdAndProfileIdAndProfileVersion(
            tenantId, snapshot.profileId(), snapshot.profileVersion()))
        .thenReturn(java.util.Optional.of(row));

    assertThat(service.historyVersion(snapshot.profileId(), 1)).isEqualTo(snapshot);
  }

  private RequirementProfileInput explicit(String minimum) {
    var value =
        new RequirementFacetValue.Width(
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
    var facet =
        new RequirementFacet(
            RequirementFacet.Kind.WIDTH,
            "finished",
            RequirementFacet.State.BOUNDED,
            RequirementFacet.Comparison.MINIMUM,
            value,
            new RequirementFacet.DecisionBasis(
                RequirementFacet.DecisionBasis.Source.CUSTOMER_INSTRUCTION,
                "customer-contract",
                UUID.randomUUID(),
                Instant.parse("2026-09-18T10:00:00Z")));
    return new RequirementProfileInput(
        new RequirementProfileBasis(
            RequirementProfileBasis.Kind.LINE_EXPLICIT,
            null,
            null,
            null,
            null,
            UUID.randomUUID(),
            Instant.parse("2026-09-18T10:00:00Z"),
            "line-explicit"),
        "fabric-v1",
        "sales-req-v1",
        Set.of("WIDTH:FINISHED"),
        List.of(facet),
        List.of(),
        List.of());
  }
}
