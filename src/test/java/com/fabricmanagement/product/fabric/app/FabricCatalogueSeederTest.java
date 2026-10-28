package com.fabricmanagement.product.fabric.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fabric.app.bootstrap.FabricCatalogueSeeder;
import com.fabricmanagement.product.fabric.app.bootstrap.FabricCatalogueSeeder.ConstructionSeed;
import com.fabricmanagement.product.fabric.app.bootstrap.FabricCatalogueSeeder.FinishingSeed;
import com.fabricmanagement.product.fabric.app.bootstrap.FabricCatalogueSeeder.PriceClassSeed;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricConstructionFamily;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The constructions FabricOS ships: one per code, every family covered, British names. */
class FabricCatalogueSeederTest {

  private static final List<ConstructionSeed> SEEDS = FabricCatalogueSeeder.constructions();

  @Test
  void codesAreUniqueUpperSnakeCase() {
    assertThat(SEEDS).extracting(ConstructionSeed::code).doesNotHaveDuplicates();
    assertThat(SEEDS)
        .extracting(ConstructionSeed::code)
        .allMatch(code -> code.matches("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$"));
  }

  @Test
  void everyFamilyHasAtLeastOneConstruction() {
    assertThat(SEEDS)
        .extracting(ConstructionSeed::family)
        .containsAll(Arrays.asList(FabricConstructionFamily.values()));
  }

  @Test
  void theDemoFabricsFindTheirConstructions() {
    assertThat(SEEDS)
        .extracting(ConstructionSeed::code)
        .contains("POPLIN", "TWILL_2_1", "SATEEN", "PLAIN", "SINGLE_JERSEY", "GABARDINE");
  }

  @Test
  void wovenComeBeforeKnittedBeforeNonwovenInDisplayOrder() {
    int lastWoven =
        SEEDS.stream()
            .filter(seed -> seed.family().formation() == FabricFormation.WOVEN)
            .mapToInt(ConstructionSeed::displayOrder)
            .max()
            .orElseThrow();
    int firstKnitted =
        SEEDS.stream()
            .filter(seed -> seed.family().formation() == FabricFormation.KNITTED)
            .mapToInt(ConstructionSeed::displayOrder)
            .min()
            .orElseThrow();
    assertThat(lastWoven).isLessThan(firstKnitted);
  }

  @Test
  void namesAreBritishEnglish() {
    Pattern turkishLetters = Pattern.compile("[ğĞıİşŞçÇöÖüÜ]");
    assertThat(SEEDS)
        .allSatisfy(
            seed -> {
              assertThat(turkishLetters.matcher(seed.name()).find()).as(seed.code()).isFalse();
              assertThat(turkishLetters.matcher(seed.description()).find())
                  .as(seed.code())
                  .isFalse();
            });
  }

  private static final List<FinishingSeed> FINISHING = FabricCatalogueSeeder.finishingProcesses();

  @Test
  void finishingCodesAreUniqueAndEveryCategoryIsCovered() {
    assertThat(FINISHING).extracting(FinishingSeed::code).doesNotHaveDuplicates();
    assertThat(FINISHING)
        .extracting(FinishingSeed::code)
        .allMatch(code -> code.matches("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$"));
    assertThat(FINISHING)
        .extracting(FinishingSeed::category)
        .containsAll(Arrays.asList(FabricFinishingCategory.values()));
  }

  @Test
  void theCommonWetAndDryProcessesAreProvided() {
    assertThat(FINISHING)
        .extracting(FinishingSeed::code)
        .contains(
            "MERCERISING",
            "PIECE_DYEING",
            "PIGMENT_PRINTING",
            "SANFORISING",
            "RAISING",
            "WATER_REPELLENT",
            "COATING");
  }

  @Test
  void finishingNamesAreBritishEnglishAndOrderedByCategory() {
    Pattern turkishLetters = Pattern.compile("[ğĞıİşŞçÇöÖüÜ]");
    assertThat(FINISHING)
        .allSatisfy(
            seed -> {
              assertThat(turkishLetters.matcher(seed.name()).find()).as(seed.code()).isFalse();
              assertThat(turkishLetters.matcher(seed.description()).find())
                  .as(seed.code())
                  .isFalse();
            });
    assertThat(FINISHING)
        .isSortedAccordingTo(
            java.util.Comparator.comparing(FinishingSeed::category)
                .thenComparingInt(FinishingSeed::displayOrder));
  }

  @Test
  void onlyDyeingAndPrintingColourALotAndOnlyCoatingChangesTheLabel() {
    assertThat(
            Arrays.stream(FabricFinishingCategory.values())
                .filter(FabricFinishingCategory::colours))
        .containsExactlyInAnyOrder(
            FabricFinishingCategory.DYEING, FabricFinishingCategory.PRINTING);
    assertThat(
            Arrays.stream(FabricFinishingCategory.values())
                .filter(FabricFinishingCategory::affectsLabel))
        .containsExactly(FabricFinishingCategory.COATING);
  }

  @Test
  void aFinishingProcessKeepsItsCodeAndCategoryWhenTheTenantEditsIt() {
    FabricFinishingProcess process =
        FabricFinishingProcess.defineTenant(
            UUID.randomUUID(),
            "ENZYME_WASH",
            "Enzyme wash",
            null,
            null,
            FabricFinishingCategory.CHEMICAL_FINISH,
            FinishingChargeUnit.KG);
    process.update("Bio-polishing", "Enzyme treatment", 7, FinishingChargeUnit.M, false);

    assertThat(process.getCode()).isEqualTo("ENZYME_WASH");
    assertThat(process.getCategory()).isEqualTo(FabricFinishingCategory.CHEMICAL_FINISH);
    assertThat(process.getName()).isEqualTo("Bio-polishing");
    assertThat(process.getChargeUnit()).isEqualTo(FinishingChargeUnit.M);
    assertThat(process.isUsable()).isFalse();
    assertThatThrownBy(
            () ->
                FabricFinishingProcess.defineTenant(
                    UUID.randomUUID(),
                    "enzyme wash",
                    "Enzyme wash",
                    null,
                    null,
                    FabricFinishingCategory.CHEMICAL_FINISH,
                    FinishingChargeUnit.KG))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(
            () ->
                FabricFinishingProcess.defineTenant(
                    UUID.randomUUID(),
                    "ENZYME_WASH",
                    "Enzyme wash",
                    null,
                    null,
                    FabricFinishingCategory.CHEMICAL_FINISH,
                    null))
        .isInstanceOf(FabricDomainException.class);
  }

  private static final List<PriceClassSeed> PRICE_CLASSES = FabricCatalogueSeeder.priceClasses();

  @Test
  void priceClassCodesAreUniqueUpperSnakeCase() {
    assertThat(PRICE_CLASSES).extracting(PriceClassSeed::code).doesNotHaveDuplicates();
    assertThat(PRICE_CLASSES)
        .extracting(PriceClassSeed::code)
        .allMatch(code -> code.matches("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$"));
    assertThat(PRICE_CLASSES).extracting(PriceClassSeed::displayOrder).doesNotHaveDuplicates();
  }

  @Test
  void theDyeStrengthTiersAndTheColoursDyehousesListAsLinesOfTheirOwnAreProvided() {
    assertThat(PRICE_CLASSES)
        .extracting(PriceClassSeed::code)
        .containsExactly("LIGHT", "MEDIUM", "DARK", "EXTRA_DARK", "BLACK", "ROYAL", "TURQUOISE");
  }

  @Test
  void priceClassNamesAreBritishEnglish() {
    Pattern turkishLetters = Pattern.compile("[ğĞıİşŞçÇöÖüÜ]");
    assertThat(PRICE_CLASSES)
        .allSatisfy(
            seed -> {
              assertThat(turkishLetters.matcher(seed.name()).find()).as(seed.code()).isFalse();
              assertThat(turkishLetters.matcher(seed.description()).find())
                  .as(seed.code())
                  .isFalse();
            });
  }
}
