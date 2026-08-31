package com.fabricmanagement.product.yarn.domain.article;

import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.SERIALIZER;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.TENANT;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.draft;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.fiber;
import static com.fabricmanagement.product.yarn.domain.article.YarnArticleTestFixtures.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountBasis;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountSystem;
import com.fabricmanagement.product.yarn.domain.vocabulary.FilamentForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnMaterialForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnStructureType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class YarnArticleDerivationTest {

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void derivesDaResultantAndComponentTimesFoldBranches() {
    Fiber fiber = fiber("000000000001", MaterialSource.VIRGIN);
    var resultant = YarnArticleTestFixtures.validSingle(fiber);
    resultant.countBasis = CountBasis.RESULTANT;
    YarnArticle first = draft(resultant);

    var component = new YarnArticleTestFixtures.SpecBuilder();
    component.structureType = YarnStructureType.PLIED;
    component.foldCount = 2;
    component.originalCountValue = new BigDecimal("20");
    YarnArticle second = draft(component);

    assertThat(first.getResultantLinearDensityTex()).isEqualByComparingTo("20.00");
    assertThat(second.getResultantLinearDensityTex()).isEqualByComparingTo("40.00");
  }

  @Test
  void derivesDbSumAndReciprocalContractionWorkedCase() {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.originalCountSystem = null;
    builder.originalCountValue = null;
    builder.countBasis = CountBasis.RESULTANT;
    builder.structureType = YarnStructureType.PLIED;
    builder.foldCount = 2;
    builder.contraction = new BigDecimal("5");
    builder.components.add(strand(1, "20"));
    builder.components.add(strand(2, "20"));

    YarnArticle article = draft(builder);

    assertThat(article.getResultantLinearDensityTex()).isEqualByComparingTo("42.11");
    assertThat(article.getCanonicalDesignation()).isEqualTo("tex 42.11");
  }

  @Test
  void acceptsNinetyNinePointNineNineAndRejectsOneHundredContraction() {
    var accepted = new YarnArticleTestFixtures.SpecBuilder();
    accepted.contraction = new BigDecimal("99.99");
    assertThat(draft(accepted).getResultantLinearDensityTex()).isEqualByComparingTo("200000.00");

    var rejected = new YarnArticleTestFixtures.SpecBuilder();
    rejected.contraction = new BigDecimal("100");
    assertThatThrownBy(() -> draft(rejected))
        .isInstanceOf(YarnDomainException.class)
        .satisfies(
            error ->
                assertThat(((YarnDomainException) error).getInvariantIds()).containsExactly("I28"));
  }

  @Test
  void physicalBoundsAndPairingFailBeforeRegistryConversion() {
    var zero = new YarnArticleTestFixtures.SpecBuilder();
    zero.originalCountValue = BigDecimal.ZERO;
    assertInvariant(zero, "I29");

    var negative = new YarnArticleTestFixtures.SpecBuilder();
    negative.originalCountValue = new BigDecimal("-1");
    assertInvariant(negative, "I29");

    var zeroFilaments = filamentBuilder(0);
    assertInvariant(zeroFilaments, "I29");
    var negativeFilaments = filamentBuilder(-1);
    assertInvariant(negativeFilaments, "I29");

    var halfPair = new YarnArticleTestFixtures.SpecBuilder();
    halfPair.originalCountSystem = null;
    assertInvariant(halfPair, "I30");

    var zeroStrand = new YarnArticleTestFixtures.SpecBuilder();
    zeroStrand.structureType = YarnStructureType.PLIED;
    zeroStrand.foldCount = 2;
    zeroStrand.components.add(strand(1, "0"));
    zeroStrand.components.add(strand(2, "20"));
    assertInvariant(zeroStrand, "I29");
  }

  @Test
  void canonicalDesignationGrammarIsVerbatim() {
    assertThat(designation(CountSystem.NE, "30", 2, CountBasis.COMPONENT, null))
        .isEqualTo("Ne 30/2");
    assertThat(designation(CountSystem.NE, "30", 2, CountBasis.RESULTANT, null))
        .isEqualTo("Ne 30/2R");
    assertThat(designation(CountSystem.DTEX, "167", 1, CountBasis.COMPONENT, 48))
        .isEqualTo("dtex 167 f48");
    assertThat(designation(CountSystem.TEX, "20", 1, CountBasis.COMPONENT, null))
        .isEqualTo("tex 20");
  }

  private static YarnArticleSpec.ComponentInput strand(int index, String value) {
    return new YarnArticleSpec.ComponentInput(
        ComponentKind.STRAND, index, null, CountSystem.TEX, new BigDecimal(value), null, null);
  }

  private static void assertInvariant(YarnArticleTestFixtures.SpecBuilder builder, String id) {
    assertThatThrownBy(() -> draft(builder))
        .isInstanceOf(YarnDomainException.class)
        .satisfies(
            error -> assertThat(((YarnDomainException) error).getInvariantIds()).contains(id));
  }

  private static YarnArticleTestFixtures.SpecBuilder filamentBuilder(int filamentCount) {
    var builder = new YarnArticleTestFixtures.SpecBuilder();
    builder.materialForm = YarnMaterialForm.CONTINUOUS_FILAMENT;
    builder.spinningFamily = null;
    builder.filamentForm = FilamentForm.FLAT;
    builder.filamentCount = filamentCount;
    return builder;
  }

  private static String designation(
      CountSystem system, String value, int fold, CountBasis basis, Integer filamentCount) {
    TenantContext.setCurrentTenantId(TENANT);
    return YarnArticle.createDraft(
            product(true),
            "Designation",
            null,
            new YarnArticleSpec(
                system,
                new BigDecimal(value),
                basis,
                fold == 1 ? YarnStructureType.SINGLE : YarnStructureType.PLIED,
                fold,
                filamentCount,
                null,
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                List.of(),
                List.of(),
                List.of()),
            SERIALIZER)
        .getCanonicalDesignation();
  }
}
