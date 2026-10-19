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
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricYarnRole;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
            null,
            null,
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
        .as("colour state and finished weight are recommended settings, not required")
        .containsExactly(
            FabricArticle.CONSTRUCTION_MISSING,
            FabricArticle.THREAD_DENSITY_MISSING,
            FabricArticle.COMPOSITION_INCOMPLETE);
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
  void anActiveArticleStaysCompleteAndOnlyAnIdentityChangeRaisesItsSpecVersion() {
    FabricArticle article = active();

    article.apply(spec(twill, "220.0", null, null));
    assertThat(article.getArticleSpecVersion()).as("220 and 220.0 are the same").isEqualTo(1);

    article.apply(spec(twill, "230", "150", "148"));
    assertThat(article.getArticleSpecVersion())
        .as("finished weight and widths are recommended settings")
        .isEqualTo(1);

    article.apply(withGreige(spec(twill, "230", "150", "148"), "250"));
    assertThat(article.getArticleSpecVersion())
        .as("filling in the greige weight completes the fabric")
        .isEqualTo(1);

    article.apply(withGreige(spec(twill, "230", "150", "148"), "260"));
    assertThat(article.getArticleSpecVersion()).isEqualTo(2);

    article.apply(
        withWoven(
            withGreige(spec(twill, "230", "150", "148"), "260"),
            new FabricArticleSpec.Woven(new BigDecimal("54"), new BigDecimal("28"), null, "2/1")));
    assertThat(article.getArticleSpecVersion()).as("a denser warp is another fabric").isEqualTo(3);

    assertThatThrownBy(
            () ->
                article.apply(
                    withWoven(spec(twill, "230", null, null), FabricArticleSpec.Woven.EMPTY)))
        .as("an active woven fabric keeps its ends and picks")
        .isInstanceOf(FabricDomainException.class);
  }

  @Test
  void aKnittedFabricNeedsItsGaugeButNotItsMachineDiameter() {
    FabricArticle knitted = FabricArticle.draft(fabric(), FabricFormation.KNITTED);
    knitted.apply(knittedSpec(null, "30", 90));
    assertThat(knitted.activationProblems()).containsExactly(FabricArticle.MACHINE_GAUGE_MISSING);

    knitted.apply(knittedSpec(28, "30", 90));
    knitted.activate();
    knitted.apply(knittedSpec(28, "34", 102));
    assertThat(knitted.getArticleSpecVersion())
        .as("the same jersey runs on a 30 or 34 inch machine")
        .isEqualTo(1);
    knitted.apply(knittedSpec(24, "34", 102));
    assertThat(knitted.getArticleSpecVersion()).isEqualTo(2);
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

  @Test
  void anActiveFabricDeclaresACompositionOfExactlyOneHundredPercent() {
    FabricArticle article = FabricArticle.draft(fabric(), FabricFormation.WOVEN);
    article.apply(with(spec(twill, "220", null, null), List.of(), List.of(fibre(CO, "60"))));
    assertThat(article.activationProblems()).containsExactly(FabricArticle.COMPOSITION_INCOMPLETE);
    assertThatThrownBy(article::activate).isInstanceOf(FabricDomainException.class);

    assertThatThrownBy(
            () ->
                article.apply(
                    with(
                        spec(twill, "220", null, null),
                        List.of(),
                        List.of(fibre(CO, "60"), fibre(LI, "45")))))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("more than 100");
    assertThatThrownBy(
            () ->
                article.apply(
                    with(
                        spec(twill, "220", null, null),
                        List.of(),
                        List.of(fibre(CO, "50"), fibre(CO, "50")))))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("twice");
    assertThatThrownBy(
            () ->
                article.apply(
                    with(spec(twill, "220", null, null), List.of(), List.of(fibre(CO, "99.995")))))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("two decimal places");

    article.apply(
        with(spec(twill, "220", null, null), List.of(), List.of(fibre(LI, "55"), fibre(CO, "45"))));
    article.activate();
    assertThat(article.compositionLines())
        .extracting(FabricArticleSpec.CompositionLine::isoCode)
        .containsExactly("LI", "CO");
    assertThatThrownBy(
            () ->
                article.apply(
                    with(spec(twill, "220", null, null), List.of(), List.of(fibre(LI, "55")))))
        .as("an active fabric keeps a full composition")
        .isInstanceOf(FabricDomainException.class);
  }

  @Test
  void yarnsAreOptionalButTheirRolesFitTheFormationAndTheirSharesMakeOneHundred() {
    UUID warpYarn = UUID.randomUUID();
    UUID weftYarn = UUID.randomUUID();
    FabricArticle article = FabricArticle.draft(fabric(), FabricFormation.WOVEN);

    assertThatThrownBy(
            () ->
                article.apply(
                    with(
                        spec(twill, "220", null, null),
                        List.of(yarn(FabricYarnRole.KNIT_FEED, warpYarn, "100")),
                        cotton())))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("does not fit");
    assertThatThrownBy(
            () ->
                article.apply(
                    with(
                        spec(twill, "220", null, null),
                        List.of(
                            yarn(FabricYarnRole.WARP, warpYarn, "70"),
                            yarn(FabricYarnRole.WEFT, weftYarn, "40")),
                        cotton())))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("more than 100");
    assertThatThrownBy(
            () ->
                article.apply(
                    with(
                        spec(twill, "220", null, null),
                        List.of(
                            yarn(FabricYarnRole.WARP, warpYarn, "30"),
                            yarn(FabricYarnRole.WARP, warpYarn, "30")),
                        cotton())))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("twice");

    article.apply(
        with(
            spec(twill, "220", null, null),
            List.of(yarn(FabricYarnRole.WARP, warpYarn, "60")),
            cotton()));
    assertThat(article.activationProblems()).containsExactly(FabricArticle.YARN_SHARES_INCOMPLETE);

    article.apply(
        with(
            spec(twill, "220", null, null),
            List.of(
                yarn(FabricYarnRole.WEFT, weftYarn, "40"),
                yarn(FabricYarnRole.WARP, warpYarn, "60")),
            cotton()));
    article.activate();
    assertThat(article.yarnLines())
        .extracting(FabricArticleSpec.YarnLine::role)
        .as("listed in role order")
        .containsExactly(FabricYarnRole.WARP, FabricYarnRole.WEFT);
  }

  @Test
  void resavingTheSameLinesKeepsTheRowsAndTheSpecVersion() {
    UUID warpYarn = UUID.randomUUID();
    FabricArticle article = FabricArticle.draft(fabric(), FabricFormation.WOVEN);
    FabricArticleSpec blend =
        with(
            spec(twill, "220", null, null),
            List.of(yarn(FabricYarnRole.WARP, warpYarn, "100")),
            List.of(fibre(CO, "45"), fibre(LI, "55")));
    article.apply(blend);
    article.activate();
    List<Object> compositionRows = rows(article, "composition");
    List<Object> yarnRows = rows(article, "yarns");

    article.apply(
        with(
            spec(twill, "220", null, null),
            List.of(yarn(FabricYarnRole.WARP, warpYarn, "100.00")),
            List.of(fibre(LI, "55.00"), fibre(CO, "45"))));
    assertThat(article.getArticleSpecVersion()).as("same lines, other order").isEqualTo(1);
    assertThat(rows(article, "composition")).containsExactlyInAnyOrderElementsOf(compositionRows);
    assertThat(rows(article, "yarns")).containsExactlyElementsOf(yarnRows);

    article.apply(
        with(
            spec(twill, "220", null, null),
            List.of(yarn(FabricYarnRole.WARP, warpYarn, "100")),
            List.of(fibre(LI, "50"), fibre(CO, "50"))));
    assertThat(article.getArticleSpecVersion()).isEqualTo(2);
    assertThat(article.compositionLines())
        .extracting(FabricArticleSpec.CompositionLine::percentage)
        .allSatisfy(value -> assertThat(value).isEqualByComparingTo("50"));
  }

  /** The child rows themselves, to see that a re-save keeps them rather than replacing them. */
  private static List<Object> rows(FabricArticle article, String field) {
    return new ArrayList<>((List<?>) ReflectionTestUtils.getField(article, field));
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
        gsm == null ? null : WOVEN_52_28,
        null,
        null,
        gsm == null ? List.of() : cotton());
  }

  private static final FabricArticleSpec.Woven WOVEN_52_28 =
      new FabricArticleSpec.Woven(new BigDecimal("52"), new BigDecimal("28"), null, "2/1");

  private FabricArticleSpec knittedSpec(Integer gauge, String diameter, Integer feeders) {
    return new FabricArticleSpec(
        "PM-1458",
        jersey,
        FabricColourState.PIECE_DYED,
        new BigDecimal("160"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        new FabricArticleSpec.Knitted(
            gauge, new BigDecimal(diameter), null, null, new BigDecimal("2.75"), feeders),
        List.of(),
        cotton());
  }

  private static FabricArticleSpec withGreige(FabricArticleSpec spec, String greige) {
    return new FabricArticleSpec(
        spec.articleCode(),
        spec.construction(),
        spec.colourState(),
        spec.gsmFinished(),
        new BigDecimal(greige),
        spec.nominalFinishedWidthCm(),
        spec.usableWidthCm(),
        spec.nominalGreigeWidthCm(),
        spec.standardRollLengthM(),
        spec.remarks(),
        spec.woven(),
        spec.knitted(),
        spec.yarns(),
        spec.composition());
  }

  private static FabricArticleSpec withWoven(
      FabricArticleSpec spec, FabricArticleSpec.Woven woven) {
    return new FabricArticleSpec(
        spec.articleCode(),
        spec.construction(),
        spec.colourState(),
        spec.gsmFinished(),
        spec.gsmGreige(),
        spec.nominalFinishedWidthCm(),
        spec.usableWidthCm(),
        spec.nominalGreigeWidthCm(),
        spec.standardRollLengthM(),
        spec.remarks(),
        woven,
        spec.knitted(),
        spec.yarns(),
        spec.composition());
  }

  private static FabricArticleSpec with(
      FabricArticleSpec spec,
      List<FabricArticleSpec.YarnLine> yarns,
      List<FabricArticleSpec.CompositionLine> composition) {
    return new FabricArticleSpec(
        spec.articleCode(),
        spec.construction(),
        spec.colourState(),
        spec.gsmFinished(),
        spec.gsmGreige(),
        spec.nominalFinishedWidthCm(),
        spec.usableWidthCm(),
        spec.nominalGreigeWidthCm(),
        spec.standardRollLengthM(),
        spec.remarks(),
        spec.woven(),
        spec.knitted(),
        yarns,
        composition);
  }

  private static final UUID CO = UUID.randomUUID();
  private static final UUID LI = UUID.randomUUID();

  private static List<FabricArticleSpec.CompositionLine> cotton() {
    return List.of(fibre(CO, "100"));
  }

  private static FabricArticleSpec.CompositionLine fibre(UUID fiberId, String percentage) {
    return new FabricArticleSpec.CompositionLine(
        fiberId,
        fiberId.equals(CO) ? "CO" : "LI",
        fiberId.equals(CO) ? "Cotton" : "Linen",
        MaterialSource.VIRGIN,
        new BigDecimal(percentage));
  }

  private static FabricArticleSpec.YarnLine yarn(
      FabricYarnRole role, UUID yarnProductId, String share) {
    return new FabricArticleSpec.YarnLine(role, yarnProductId, new BigDecimal(share), "Ne 40/1 CO");
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
