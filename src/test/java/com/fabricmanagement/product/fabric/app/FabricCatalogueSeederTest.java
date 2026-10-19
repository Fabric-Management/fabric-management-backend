package com.fabricmanagement.product.fabric.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.fabric.app.bootstrap.FabricCatalogueSeeder;
import com.fabricmanagement.product.fabric.app.bootstrap.FabricCatalogueSeeder.ConstructionSeed;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricConstructionFamily;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import java.util.Arrays;
import java.util.List;
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
}
