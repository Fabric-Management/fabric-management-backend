package com.fabricmanagement.product.fabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fabric.domain.article.FabricArticle;
import com.fabricmanagement.product.fabric.domain.article.FabricArticleSpec;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.reference.FabricConstruction;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricArticleStatus;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricColourState;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricConstructionFamily;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** FABRIC-1: the fabric article's rules, without a database. */
class FabricArticleTest {

  private static final UUID TENANT = UUID.randomUUID();

  private final FabricConstruction twill =
      construction("TWILL_3_1", "3/1 twill", FabricConstructionFamily.TWILL);
  private final FabricConstruction jersey =
      construction("SINGLE_JERSEY", "Single jersey", FabricConstructionFamily.SINGLE_JERSEY);

  @Test
  void aDraftBelongsToAFabricProductAndGetsTheDetailOfItsFormation() {
    FabricArticle woven = FabricArticle.draft(fabric(), FabricFormation.WOVEN);
    FabricArticle knitted = FabricArticle.draft(fabric(), FabricFormation.KNITTED);
    FabricArticle nonwoven = FabricArticle.draft(fabric(), FabricFormation.NONWOVEN);

    assertThat(woven.getStatus()).isEqualTo(FabricArticleStatus.DRAFT);
    assertThat(woven.getWoven()).isNotNull();
    assertThat(woven.getKnitted()).isNull();
    assertThat(knitted.getKnitted()).isNotNull();
    assertThat(knitted.getWoven()).isNull();
    assertThat(nonwoven.getWoven()).isNull();
    assertThat(nonwoven.getKnitted()).isNull();
    assertThatThrownBy(
            () ->
                FabricArticle.draft(
                    Product.create(ProductType.YARN, "KG", "Yarn", null), FabricFormation.WOVEN))
        .isInstanceOf(FabricDomainException.class);
  }

  @Test
  void aConstructionMustFitTheFormation() {
    FabricArticle woven = FabricArticle.draft(fabric(), FabricFormation.WOVEN);

    assertThatThrownBy(() -> woven.apply(spec(jersey, "120", null, null)))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("does not fit");
  }

  @Test
  void onlyTheFormationsOwnDetailIsAccepted() {
    FabricArticle knitted = FabricArticle.draft(fabric(), FabricFormation.KNITTED);
    FabricArticleSpec withWoven =
        new FabricArticleSpec(
            null,
            jersey,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new FabricArticleSpec.Woven(new BigDecimal("40"), null, null, null),
            null);

    assertThatThrownBy(() -> knitted.apply(withWoven))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("woven");
  }

  @Test
  void theUsableWidthCannotExceedTheFinishedWidth() {
    FabricArticle woven = FabricArticle.draft(fabric(), FabricFormation.WOVEN);

    assertThatThrownBy(() -> woven.apply(spec(twill, "220", "150", "152")))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("usable width");
    woven.apply(spec(twill, "220", "150", "148"));
    assertThat(woven.getUsableWidthCm()).isEqualByComparingTo("148");
  }

  @Test
  void aDraftMayBeIncompleteButOnlyACompleteOneActivates() {
    FabricArticle article = FabricArticle.draft(fabric(), FabricFormation.WOVEN);
    article.apply(spec(null, null, null, null));

    assertThat(article.activationProblems())
        .containsExactly(
            FabricArticle.CONSTRUCTION_MISSING,
            FabricArticle.COLOUR_STATE_MISSING,
            FabricArticle.GSM_FINISHED_MISSING);
    assertThatThrownBy(article::activate)
        .isInstanceOfSatisfying(
            FabricDomainException.class,
            problem -> {
              assertThat(problem.getHttpStatus()).isEqualTo(409);
              assertThat(problem.getProblems()).hasSize(3);
            });

    article.apply(spec(twill, "220", null, null));
    article.activate();
    assertThat(article.getStatus()).isEqualTo(FabricArticleStatus.ACTIVE);
    assertThat(article.activationProblems()).isEmpty();
  }

  @Test
  void anActiveArticleStaysCompleteAndARealChangeRaisesItsSpecVersion() {
    FabricArticle article = active();

    article.apply(spec(twill, "220.0", null, null));
    assertThat(article.getArticleSpecVersion()).as("120 and 120.0 are the same").isEqualTo(1);

    article.apply(spec(twill, "230", null, null));
    assertThat(article.getArticleSpecVersion()).isEqualTo(2);

    assertThatThrownBy(() -> article.apply(spec(twill, null, null, null)))
        .isInstanceOf(FabricDomainException.class);
  }

  @Test
  void onlyADraftIsDeletedAndOnlyAnActiveArticleIsMadeObsolete() {
    FabricArticle draft = FabricArticle.draft(fabric(), FabricFormation.WOVEN);
    assertThatThrownBy(draft::obsolete).isInstanceOf(FabricDomainException.class);
    draft.delete(Instant.parse("2026-10-19T10:00:00Z"));
    assertThat(draft.getDeletedAt()).isNotNull();
    assertThat(draft.getIsActive()).isFalse();

    FabricArticle active = active();
    assertThatThrownBy(() -> active.delete(Instant.now()))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("obsolete");
    active.obsolete();
    assertThat(active.getStatus()).isEqualTo(FabricArticleStatus.OBSOLETE);
    assertThatThrownBy(() -> active.apply(spec(twill, "220", null, null)))
        .isInstanceOf(FabricDomainException.class);
  }

  @Test
  void aRetiredConstructionCannotBeChosenButAnArticleKeepsTheOneItHas() {
    FabricArticle article = active();
    twill.update(twill.getName(), null, null, false);

    article.apply(spec(twill, "220", null, null));
    FabricConstruction otherRetired =
        construction("TWILL_2_1", "2/1 twill", FabricConstructionFamily.TWILL);
    otherRetired.update(otherRetired.getName(), null, null, false);
    assertThatThrownBy(() -> article.apply(spec(otherRetired, "220", null, null)))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("no longer in use");
  }

  @Test
  void aConstructionTakesItsFormationFromItsFamilyAndKeepsItsCode() {
    assertThat(twill.getFormation()).isEqualTo(FabricFormation.WOVEN);
    assertThat(jersey.getFormation()).isEqualTo(FabricFormation.KNITTED);
    twill.update("3/1 S twill", "Renamed by the tenant", 5, true);
    assertThat(twill.getCode()).isEqualTo("TWILL_3_1");
    assertThat(twill.getName()).isEqualTo("3/1 S twill");
    assertThatThrownBy(
            () ->
                FabricConstruction.defineTenant(
                    TENANT, "twill 3/1", "x", null, null, FabricConstructionFamily.TWILL))
        .isInstanceOf(FabricDomainException.class);
  }

  private FabricArticle active() {
    FabricArticle article = FabricArticle.draft(fabric(), FabricFormation.WOVEN);
    article.apply(spec(twill, "220", null, null));
    article.activate();
    return article;
  }

  private static FabricArticleSpec spec(
      FabricConstruction construction, String gsm, String finished, String usable) {
    return new FabricArticleSpec(
        "PM-1455",
        construction,
        gsm == null ? null : FabricColourState.PIECE_DYED,
        gsm == null ? null : new BigDecimal(gsm),
        null,
        finished == null ? null : new BigDecimal(finished),
        usable == null ? null : new BigDecimal(usable),
        null,
        new BigDecimal("120"),
        null,
        null,
        null);
  }

  private static Product fabric() {
    Product product = Product.create(ProductType.FABRIC, "M", "Cotton Twill 220gsm", null);
    ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    return product;
  }

  private static FabricConstruction construction(
      String code, String name, FabricConstructionFamily family) {
    FabricConstruction construction =
        FabricConstruction.defineSystem(TENANT, code, name, null, 1, family);
    ReflectionTestUtils.setField(construction, "id", UUID.randomUUID());
    return construction;
  }
}
