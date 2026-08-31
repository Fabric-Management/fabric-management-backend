package com.fabricmanagement.product.yarn.domain.article;

import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.SERIALIZER;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.draft;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.fiber;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.spinningSystem;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.validSingle;
import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountBasis;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountSystem;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistDirection;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistStageType;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnConstructionFeature;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnMaterialForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnStructureType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class YarnArticleSpecSerializerTest {

  private static final String GOLDEN_JSON =
      "{\"countBasis\":\"COMPONENT\",\"structureType\":\"SINGLE\",\"foldCount\":1,"
          + "\"filamentCount\":null,\"filamentForm\":null,\"originalCountSystem\":\"TEX\","
          + "\"originalCountValue\":20,\"twistContractionPercent\":null,"
          + "\"resultantLinearDensityTex\":20.00,\"materialForm\":\"STAPLE_SPUN\","
          + "\"spinningTechnologyFamily\":\"RING\",\"spinningSystemCode\":null,"
          + "\"constructionFeatures\":[],\"composition\":[{\"fiberId\":"
          + "\"33333333-3333-4333-8333-000000000010\",\"percentage\":100.00,"
          + "\"materialSource\":\"VIRGIN\"}],\"structureComponents\":[],"
          + "\"twistStages\":[{\"sequence\":1,\"stageType\":\"SINGLE\","
          + "\"direction\":\"Z\",\"turnsPerMeter\":800.00,\"strandComponentIndex\":null,"
          + "\"testMethodCode\":null}],\"conversionPolicies\":{"
          + "\"linearDensity\":\"linearDensity-v1\",\"twist\":\"twist-v1\"}}";
  private static final String GOLDEN_SHA256 =
      "1150a24288cbcd8f724e043d1a3b03a97011f1e8187cfec979ce60fc02b30a38";

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void goldenVectorFreezesExactBytesHashAndAlgorithmVersion() {
    Fiber cotton = fiber("000000000010", MaterialSource.VIRGIN);
    YarnArticle article = draft(validSingle(cotton));
    ObjectNode snapshot = SERIALIZER.auditSnapshot(article);
    byte[] actual = SERIALIZER.identityProjectionBytes(snapshot);

    assertThat(new String(actual, StandardCharsets.UTF_8)).isEqualTo(GOLDEN_JSON);
    assertThat(SERIALIZER.canonicalKey(snapshot)).isEqualTo(GOLDEN_SHA256);
    assertThat(article.getCanonicalKey()).isEqualTo(GOLDEN_SHA256);
    assertThat(article.getCanonicalKeyAlgorithmVersion()).isEqualTo((short) 1);
  }

  @Test
  void identityIsOnlyAProjectionOfTheAuditSnapshot() throws Exception {
    YarnArticle article = draft(validSingle(fiber("000000000010", MaterialSource.VIRGIN)));
    ObjectNode snapshot = SERIALIZER.auditSnapshot(article);
    ObjectNode projection = SERIALIZER.identityProjection(snapshot);

    assertThat(snapshot.path("documentType").asText())
        .isEqualTo(YarnArticleSpecSerializer.AUDIT_SCHEMA);
    var projectionKeys = new ArrayList<String>();
    projection.fieldNames().forEachRemaining(projectionKeys::add);
    assertThat(projectionKeys)
        .containsExactly(
            "countBasis",
            "structureType",
            "foldCount",
            "filamentCount",
            "filamentForm",
            "originalCountSystem",
            "originalCountValue",
            "twistContractionPercent",
            "resultantLinearDensityTex",
            "materialForm",
            "spinningTechnologyFamily",
            "spinningSystemCode",
            "constructionFeatures",
            "composition",
            "structureComponents",
            "twistStages",
            "conversionPolicies");
    assertThat(new String(SERIALIZER.identityProjectionBytes(snapshot), StandardCharsets.UTF_8))
        .isEqualTo(projection.toString());
  }

  @Test
  void snapshotContainsEveryAllowlistedFieldAndTheAuditOnlySuperset() {
    YarnArticle article = draft(validSingle(fiber("000000000010", MaterialSource.VIRGIN)));
    ObjectNode snapshot = SERIALIZER.auditSnapshot(article);
    ObjectNode projection = SERIALIZER.identityProjection(snapshot);

    assertThat(snapshot.has("sourceDesignation")).isTrue();
    assertThat(snapshot.has("canonicalDesignation")).isTrue();
    assertThat(snapshot.has("name")).isTrue();
    assertThat(projection.has("sourceDesignation")).isFalse();
    assertThat(projection.has("canonicalDesignation")).isFalse();
    assertThat(projection.has("name")).isFalse();
    projection
        .fieldNames()
        .forEachRemaining(
            key -> {
              if (!key.equals("spinningSystemCode")) {
                assertThat(snapshot.has(key)).as(key).isTrue();
              }
            });
  }

  @Test
  void identityIsStableUnderChildInputOrderAndChangesForSourceOrSpinningCode() {
    Fiber virginA = fiber("000000000020", MaterialSource.VIRGIN);
    Fiber virginB = fiber("000000000021", MaterialSource.VIRGIN);
    var first = validSingle(virginA);
    first.composition.clear();
    first.composition.add(new YarnArticleSpec.CompositionInput(virginA, new BigDecimal("60")));
    first.composition.add(new YarnArticleSpec.CompositionInput(virginB, new BigDecimal("40")));
    first.spinningSystem = spinningSystem("RING", SpinningTechnologyFamily.RING);
    var reordered = validSingle(virginA);
    reordered.composition.clear();
    reordered.composition.add(new YarnArticleSpec.CompositionInput(virginB, new BigDecimal("40")));
    reordered.composition.add(new YarnArticleSpec.CompositionInput(virginA, new BigDecimal("60")));
    reordered.spinningSystem = spinningSystem("RING", SpinningTechnologyFamily.RING);

    YarnArticle a = draft(first);
    YarnArticle b = draft(reordered);
    assertThat(a.getCanonicalKey()).isEqualTo(b.getCanonicalKey());

    var ringSpec = validSingle(virginA);
    ringSpec.spinningSystem = spinningSystem("RING", SpinningTechnologyFamily.RING);
    YarnArticle ring = draft(ringSpec);
    var recycledSpec = validSingle(fiber("000000000020", MaterialSource.RECYCLED));
    recycledSpec.spinningSystem = spinningSystem("RING", SpinningTechnologyFamily.RING);
    YarnArticle recycled = draft(recycledSpec);
    assertThat(recycled.getCanonicalKey()).isNotEqualTo(ring.getCanonicalKey());

    var compactSpec = validSingle(virginA);
    compactSpec.spinningSystem = spinningSystem("COMPACT", SpinningTechnologyFamily.RING);
    YarnArticle compact = draft(compactSpec);
    assertThat(compact.getCanonicalKey()).isNotEqualTo(ring.getCanonicalKey());
  }

  @Test
  void identityChangesForFiberAllocationAndStageToStrandBinding() {
    Fiber firstFiber = fiber("000000000030", MaterialSource.VIRGIN);
    Fiber secondFiber = fiber("000000000031", MaterialSource.VIRGIN);
    YarnArticle baseline = draft(plied(firstFiber, secondFiber, false, false));
    YarnArticle swappedBindings = draft(plied(firstFiber, secondFiber, false, true));
    YarnArticle coreA = draft(core(firstFiber, secondFiber, false));
    YarnArticle coreB = draft(core(firstFiber, secondFiber, true));

    assertThat(swappedBindings.getCanonicalKey()).isNotEqualTo(baseline.getCanonicalKey());
    assertThat(coreA.getCanonicalKey()).isNotEqualTo(coreB.getCanonicalKey());
  }

  private static YarnArticleTestFixtures.SpecBuilder plied(
      Fiber firstFiber, Fiber secondFiber, boolean swapFibers, boolean swapBindings) {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.originalCountSystem = CountSystem.TEX;
    builder.originalCountValue = new BigDecimal("40");
    builder.countBasis = CountBasis.RESULTANT;
    builder.structureType = YarnStructureType.PLIED;
    builder.foldCount = 2;
    builder.materialForm = YarnMaterialForm.STAPLE_SPUN;
    builder.spinningFamily = SpinningTechnologyFamily.RING;
    builder.composition.add(new YarnArticleSpec.CompositionInput(firstFiber, new BigDecimal("50")));
    builder.composition.add(
        new YarnArticleSpec.CompositionInput(secondFiber, new BigDecimal("50")));
    builder.components.add(strand(1, swapFibers ? secondFiber : firstFiber));
    builder.components.add(strand(2, swapFibers ? firstFiber : secondFiber));
    builder.twistStages.add(stage(1, swapBindings ? 2 : 1));
    builder.twistStages.add(stage(2, swapBindings ? 1 : 2));
    builder.twistStages.add(
        new YarnArticleSpec.TwistStageInput(
            TwistStageType.PLY, 3, TwistDirection.Z, new BigDecimal("400"), null, null));
    return builder;
  }

  private static YarnArticleSpec.ComponentInput strand(int index, Fiber fiber) {
    return new YarnArticleSpec.ComponentInput(
        ComponentKind.STRAND, index, null, CountSystem.TEX, new BigDecimal("20"), fiber, null);
  }

  private static YarnArticleSpec.TwistStageInput stage(int sequence, int componentIndex) {
    return new YarnArticleSpec.TwistStageInput(
        TwistStageType.SINGLE,
        sequence,
        TwistDirection.Z,
        new BigDecimal("800"),
        componentIndex,
        null);
  }

  private static YarnArticleTestFixtures.SpecBuilder core(
      Fiber firstFiber, Fiber secondFiber, boolean swap) {
    var builder = validSingle(firstFiber);
    builder.composition.clear();
    builder.composition.add(new YarnArticleSpec.CompositionInput(firstFiber, new BigDecimal("50")));
    builder.composition.add(
        new YarnArticleSpec.CompositionInput(secondFiber, new BigDecimal("50")));
    builder.features.add(YarnConstructionFeature.CORE_SPUN);
    builder.components.add(
        new YarnArticleSpec.ComponentInput(
            ComponentKind.LAYER,
            1,
            LayerRole.CORE,
            null,
            null,
            swap ? secondFiber : firstFiber,
            null));
    builder.components.add(
        new YarnArticleSpec.ComponentInput(
            ComponentKind.LAYER,
            2,
            LayerRole.SHEATH,
            null,
            null,
            swap ? firstFiber : secondFiber,
            null));
    return builder;
  }
}
