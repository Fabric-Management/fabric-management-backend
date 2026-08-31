package com.fabricmanagement.product.yarn.domain.article;

import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.SERIALIZER;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.TENANT;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.draft;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.fiber;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.product;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.spinningSystem;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.testMethod;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.validSingle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountBasis;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountSystem;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistDirection;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistStageType;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnConstructionFeature;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnStructureType;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * I-id -> test-name map (the catalogue is the single executable source): I1 i1SingleRequiresOne; I2
 * i2PliedAndCabledRequireTwo; I3 i3MultipleWoundRejectsTwist; I4 i4StrandsMatchStructureAndFold; I5
 * i5CoreSpunRequiresLayers; I6 i6StageBindsOnlyStrand; I7 i7StapleRequiresSingleStage; I8
 * i8PliedAndCabledStageShape; I9 i9DirectionMatchesTpm; I10 i10SequencesAreContiguous; I11
 * i11TestMethodPropertyKey; I12 i12MaterialFormAxes; I13 i13SpinningSystemFamily; I14
 * i14CompositionIsPurePositiveUnique; I15 i15ActivationCompleteness; I16 i16SpecVersionBumpsOnce;
 * I17 i17LifecycleIsOneWay; I18 i18ProductEligibility; I19 i19DerivedFieldsHaveNoWritePath; I20
 * i20ActiveEditRerunsFullCatalogue; I21 i21EachStrandHasOneSingleStage; I22 i22ResultantReconciles;
 * I23 i23LayersRequireCompoundFeature; I24 i24ComponentFibersAppearInComposition; I25
 * i25SnapshotsComeFromFiber; I26 i26AuditVersionSlotIsNamed; I27 i27LayersAreCountFree; I28
 * i28ContractionBounds; I29 i29PhysicalBounds; I30 i30CountPairs; I31 i31SourceSnapshotsAgree.
 */
class YarnArticleInvariantTest {

  private final Fiber cotton = fiber("000000000010", MaterialSource.VIRGIN);

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void catalogueContainsI1ThroughI31Exactly() {
    assertThat(YarnArticleInvariantCatalog.ALL_IDS)
        .containsExactlyElementsOf(
            java.util.stream.IntStream.rangeClosed(1, 31)
                .mapToObj(number -> "I" + number)
                .toList());
  }

  @Test
  void i1SingleRequiresOne() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.foldCount = 2;
    assertCreateInvariant(builder, "I1");
  }

  @Test
  void i2PliedAndCabledRequireTwo() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.structureType = YarnStructureType.PLIED;
    assertCreateInvariant(builder, "I2");
  }

  @Test
  void i3MultipleWoundRejectsTwist() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.structureType = YarnStructureType.MULTIPLE_WOUND;
    builder.foldCount = 2;
    builder.twistStages.add(stage(TwistStageType.PLY, 1, null));
    assertCreateInvariant(builder, "I3");
  }

  @Test
  void i4StrandsMatchStructureAndFold() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.components.add(strand(1, null));
    assertCreateInvariant(builder, "I4");
  }

  @Test
  void i5CoreSpunRequiresLayers() {
    var builder = validSingle(cotton);
    builder.features.add(YarnConstructionFeature.CORE_SPUN);
    assertCreateInvariant(builder, "I5");
  }

  @Test
  void singleCoreSpunWithCountFreeCoreAndSheathActivates() {
    var builder = validSingle(cotton);
    builder.features.add(YarnConstructionFeature.CORE_SPUN);
    builder.components.add(layer(1, LayerRole.CORE, cotton));
    builder.components.add(layer(2, LayerRole.SHEATH, cotton));

    YarnArticle article = draft(builder);
    article.activate();

    assertThat(article.getStatus()).isEqualTo(YarnArticleStatus.ACTIVE);
    assertThat(article.getFoldCount()).isEqualTo(1);
    assertThat(article.getStructureComponents())
        .allSatisfy(
            layer -> {
              assertThat(layer.getKind()).isEqualTo(ComponentKind.LAYER);
              assertThat(layer.getComponentCountSystem()).isNull();
              assertThat(layer.getComponentCountValue()).isNull();
              assertThat(layer.getComponentLinearDensityTex()).isNull();
            });
  }

  @Test
  void i6StageBindsOnlyStrand() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.twistStages.add(stage(TwistStageType.SINGLE, 1, 99));
    assertCreateInvariant(builder, "I6");
  }

  @Test
  void i7StapleRequiresSingleStage() {
    var builder = validSingle(cotton);
    builder.twistStages.clear();
    assertActivationInvariant(draft(builder), "I7");
  }

  @Test
  void i8PliedAndCabledStageShape() {
    var builder = validSingle(cotton);
    builder.structureType = YarnStructureType.PLIED;
    builder.foldCount = 2;
    assertActivationInvariant(draft(builder), "I8");
  }

  @Test
  void i9DirectionMatchesTpm() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.twistStages.add(
        new YarnArticleSpec.TwistStageInput(
            TwistStageType.SINGLE, 1, TwistDirection.NONE, BigDecimal.ONE, null, null));
    assertCreateInvariant(builder, "I9");
  }

  @Test
  void i10SequencesAreContiguous() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.twistStages.add(stage(TwistStageType.SINGLE, 2, null));
    assertCreateInvariant(builder, "I10");
  }

  @Test
  void i11TestMethodPropertyKey() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.twistStages.add(
        new YarnArticleSpec.TwistStageInput(
            TwistStageType.SINGLE,
            1,
            TwistDirection.Z,
            BigDecimal.TEN,
            null,
            testMethod("WRONG", "FIBER_LENGTH")));
    assertCreateInvariant(builder, "I11");
  }

  @Test
  void i12MaterialFormAxes() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.filamentCount = 12;
    assertCreateInvariant(builder, "I12");
  }

  @Test
  void i13SpinningSystemFamily() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.spinningSystem = spinningSystem("ROTOR", SpinningTechnologyFamily.ROTOR);
    assertCreateInvariant(builder, "I13");
  }

  @Test
  void i14CompositionIsPurePositiveUnique() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.composition.add(
        new YarnArticleSpec.CompositionInput(
            fiber("000000000011", new AtomicReference<MaterialSource>(), false), BigDecimal.TEN));
    assertCreateInvariant(builder, "I14");
  }

  @Test
  void i15ActivationCompleteness() {
    assertActivationInvariant(draft(new YarnArticleTestFixtures.SpecBuilder()), "I15");
  }

  @Test
  void i16SpecVersionBumpsOnce() {
    YarnArticle article = draft(validSingle(cotton));
    article.updateSpec(validSingle(cotton).build(), SERIALIZER);
    assertThat(article.getArticleSpecVersion()).isEqualTo(2);
    article.updateMetadata("Renamed", "metadata only");
    assertThat(article.getArticleSpecVersion()).isEqualTo(2);
  }

  @Test
  void i17LifecycleIsOneWay() {
    YarnArticle article = draft(validSingle(cotton));
    article.activate();
    article.markObsolete();
    assertThatThrownBy(() -> article.updateSpec(validSingle(cotton).build(), SERIALIZER))
        .isInstanceOf(YarnDomainException.class)
        .satisfies(error -> assertHasId((YarnDomainException) error, "I17"));
  }

  @Test
  void i18ProductEligibility() {
    TenantContext.setCurrentTenantId(TENANT);
    assertThatThrownBy(
            () ->
                YarnArticle.createDraft(
                    product(false), "Bad product", null, validSingle(cotton).build(), SERIALIZER))
        .isInstanceOf(YarnDomainException.class)
        .satisfies(error -> assertHasId((YarnDomainException) error, "I18"));
  }

  @Test
  void i19DerivedFieldsHaveNoWritePath() {
    Set<String> forbidden =
        Set.of("setResultantLinearDensityTex", "setCanonicalDesignation", "setCanonicalKey");
    assertThat(YarnArticle.class.getMethods())
        .extracting("name")
        .doesNotContainAnyElementsOf(forbidden);
    assertThat(YarnArticleStructureComponent.class.getMethods())
        .extracting("name")
        .doesNotContain("setComponentLinearDensityTex");
  }

  @Test
  void i20ActiveEditRerunsFullCatalogue() {
    YarnArticle article = draft(validSingle(cotton));
    article.activate();
    var invalid = validSingle(cotton);
    invalid.twistStages.clear();
    assertThatThrownBy(() -> article.updateSpec(invalid.build(), SERIALIZER))
        .isInstanceOf(YarnDomainException.class);
  }

  @Test
  void i21EachStrandHasOneSingleStage() {
    var builder = validPlied(cotton);
    builder.twistStages.clear();
    builder.twistStages.add(stage(TwistStageType.SINGLE, 1, null));
    builder.twistStages.add(stage(TwistStageType.PLY, 2, null));
    assertActivationInvariant(draft(builder), "I21");
  }

  @Test
  void i22ResultantReconciles() {
    var builder = validPlied(cotton);
    builder.originalCountValue = new BigDecimal("41");
    assertActivationInvariant(draft(builder), "I22");
  }

  @Test
  void i23LayersRequireCompoundFeature() {
    var builder = validSingle(cotton);
    builder.components.add(layer(1, LayerRole.CORE, cotton));
    builder.components.add(layer(2, LayerRole.SHEATH, cotton));
    assertCreateInvariant(builder, "I23");
  }

  @Test
  void i24ComponentFibersAppearInComposition() {
    Fiber other = fiber("000000000012", MaterialSource.RECYCLED);
    var builder = validPlied(cotton);
    builder.components.clear();
    builder.components.add(strand(1, other));
    builder.components.add(strand(2, other));
    assertCreateInvariant(builder, "I24");
  }

  @Test
  void i25SnapshotsComeFromFiber() {
    YarnArticle article = draft(validSingle(cotton));
    assertThat(article.getComposition().getFirst())
        .extracting(
            YarnArticleComposition::getFiberName,
            YarnArticleComposition::getFiberIsoCode,
            YarnArticleComposition::getMaterialSource)
        .containsExactly("Fiber 000000000010", "CO", MaterialSource.VIRGIN);
  }

  @Test
  void i26AuditVersionSlotIsNamed() throws Exception {
    assertThat(field("specVersionTo").getName()).isEqualTo("specVersionTo");
    assertThat(Set.of(YarnArticleAuditEventType.CREATED, YarnArticleAuditEventType.SPEC_UPDATED))
        .hasSize(2);
  }

  @Test
  void i27LayersAreCountFree() {
    var builder = validSingle(cotton);
    builder.features.add(YarnConstructionFeature.CORE_SPUN);
    builder.components.add(
        new YarnArticleSpec.ComponentInput(
            ComponentKind.LAYER, 1, LayerRole.CORE, CountSystem.TEX, BigDecimal.ONE, cotton, null));
    assertCreateInvariant(builder, "I27");
  }

  @Test
  void i28ContractionBounds() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.contraction = new BigDecimal("-0.01");
    assertCreateInvariant(builder, "I28");
  }

  @Test
  void i29PhysicalBounds() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.foldCount = 0;
    assertCreateInvariant(builder, "I29");
  }

  @Test
  void i30CountPairs() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.originalCountValue = null;
    assertCreateInvariant(builder, "I30");
  }

  @Test
  void i31SourceSnapshotsAgree() {
    AtomicReference<MaterialSource> source = new AtomicReference<>();
    Fiber transitioning = fiber("000000000013", source, true);
    YarnArticle article = draft(validSingle(transitioning));
    source.set(MaterialSource.RECYCLED);

    assertThatThrownBy(
            () -> article.appendStructureComponent(layer(1, LayerRole.CORE, transitioning)))
        .isInstanceOf(YarnDomainException.class)
        .satisfies(error -> assertHasId((YarnDomainException) error, "I31"));

    var rewritten = validSingle(transitioning);
    rewritten.features.add(YarnConstructionFeature.CORE_SPUN);
    rewritten.components.add(layer(1, LayerRole.CORE, transitioning));
    rewritten.components.add(layer(2, LayerRole.SHEATH, transitioning));
    article.updateSpec(rewritten.build(), SERIALIZER);
    article.activate();

    assertThat(article.getArticleSpecVersion()).isEqualTo(2);
    assertThat(article.getComposition().getFirst().getMaterialSource())
        .isEqualTo(MaterialSource.RECYCLED);
  }

  private static YarnArticleTestFixtures.SpecBuilder validPlied(Fiber fiber) {
    var builder = validSingle(fiber);
    builder.structureType = YarnStructureType.PLIED;
    builder.foldCount = 2;
    builder.countBasis = CountBasis.RESULTANT;
    builder.originalCountValue = new BigDecimal("40");
    builder.components.add(strand(1, fiber));
    builder.components.add(strand(2, fiber));
    builder.twistStages.clear();
    builder.twistStages.add(stage(TwistStageType.SINGLE, 1, 1));
    builder.twistStages.add(stage(TwistStageType.SINGLE, 2, 2));
    builder.twistStages.add(stage(TwistStageType.PLY, 3, null));
    return builder;
  }

  private static YarnArticleSpec.ComponentInput strand(int index, Fiber fiber) {
    return new YarnArticleSpec.ComponentInput(
        ComponentKind.STRAND,
        index,
        null,
        CountSystem.TEX,
        new BigDecimal("20"),
        fiber,
        "strand " + index);
  }

  private static YarnArticleSpec.ComponentInput layer(int index, LayerRole role, Fiber fiber) {
    return new YarnArticleSpec.ComponentInput(
        ComponentKind.LAYER, index, role, null, null, fiber, role.name());
  }

  private static YarnArticleSpec.TwistStageInput stage(
      TwistStageType type, int sequence, Integer componentIndex) {
    return new YarnArticleSpec.TwistStageInput(
        type, sequence, TwistDirection.Z, new BigDecimal("800"), componentIndex, null);
  }

  private static Field field(String name) throws Exception {
    return YarnArticleAudit.class.getDeclaredField(name);
  }

  private static void assertCreateInvariant(
      YarnArticleTestFixtures.SpecBuilder builder, String id) {
    assertThatThrownBy(() -> draft(builder))
        .isInstanceOf(YarnDomainException.class)
        .satisfies(error -> assertHasId((YarnDomainException) error, id));
  }

  private static void assertActivationInvariant(YarnArticle article, String id) {
    assertThatThrownBy(article::activate)
        .isInstanceOf(YarnDomainException.class)
        .satisfies(error -> assertHasId((YarnDomainException) error, id));
  }

  private static void assertHasId(YarnDomainException error, String id) {
    assertThat(error.getInvariantIds()).contains(id);
  }
}
