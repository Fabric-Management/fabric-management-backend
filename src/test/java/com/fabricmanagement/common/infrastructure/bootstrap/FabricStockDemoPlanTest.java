package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.bootstrap.FabricStockDemoPlan.Fabric;
import com.fabricmanagement.common.infrastructure.bootstrap.FabricStockDemoPlan.Lot;
import com.fabricmanagement.product.color.domain.ColorCardSpec;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The playground fabric stock is data, so its shape can be checked without a database: how many
 * fabrics, colours, widths and lots, and that the rolls look like a mill's rolls (mostly 120 m, lot
 * ends of 90 m or 135 m, a few cut short where a fault was removed).
 */
class FabricStockDemoPlanTest {

  private static List<Lot> allLots() {
    return FabricStockDemoPlan.FABRICS.stream().flatMap(fabric -> fabric.lots().stream()).toList();
  }

  private static List<Integer> allRolls() {
    return allLots().stream().flatMap(lot -> lot.rolls().stream()).toList();
  }

  @Test
  void fiveFabricsEachStockedAtBothWidths() {
    assertThat(FabricStockDemoPlan.FABRICS).hasSize(5);
    for (Fabric fabric : FabricStockDemoPlan.FABRICS) {
      assertThat(fabric.lots().stream().map(Lot::widthCm).distinct().sorted().toList())
          .as("%s is stocked at both widths", fabric.name())
          .isEqualTo(FabricStockDemoSeeder.WIDTHS_CM);
    }
  }

  @Test
  void theMainColourHasTwoOrThreeLotsAtEachWidth() {
    for (Fabric fabric : FabricStockDemoPlan.FABRICS) {
      for (int width : FabricStockDemoSeeder.WIDTHS_CM) {
        assertThat(FabricStockDemoPlan.lotsOf(fabric, fabric.mainColour(), width))
            .as("%s, %s, %d cm", fabric.name(), fabric.mainColour(), width)
            .hasSizeBetween(2, 3);
      }
    }
  }

  @Test
  void aSecondColourIsThereToChooseBetween() {
    for (Fabric fabric : FabricStockDemoPlan.FABRICS) {
      assertThat(fabric.lots().stream().map(Lot::colour).distinct().count())
          .as("%s has a second colour", fabric.name())
          .isGreaterThanOrEqualTo(2);
    }
  }

  @Test
  void everyLotRunsAboutFiveHundredToTwentyFiveHundredMetres() {
    for (Fabric fabric : FabricStockDemoPlan.FABRICS) {
      for (Lot lot : fabric.lots()) {
        assertThat(lot.metres())
            .as("%s", FabricStockDemoPlan.lotCode(fabric, lot))
            .isBetween(500, 2500);
      }
    }
  }

  @Test
  void rollsAreMostlyOneTwentyWithLotEndsAndCutRolls() {
    List<Integer> rolls = allRolls();
    double mean = rolls.stream().mapToInt(Integer::intValue).average().orElseThrow();
    double standardShare =
        rolls.stream().filter(length -> length == FabricStockDemoPlan.ROLL_M).count()
            / (double) rolls.size();

    assertThat(mean).as("average roll length in metres").isBetween(110.0, 125.0);
    assertThat(standardShare).as("share of 120 m rolls").isGreaterThanOrEqualTo(0.6);
    assertThat(rolls).contains(90, 135);
    assertThat(rolls.stream().filter(length -> length < 90).count())
        .as("rolls cut short where a fault was removed")
        .isPositive();
    assertThat(rolls).allSatisfy(length -> assertThat(length).isBetween(40, 140));
  }

  @Test
  void everyLotHasAnOddRollAtItsEndAndAtMostOneRemainder() {
    for (Fabric fabric : FabricStockDemoPlan.FABRICS) {
      for (Lot lot : fabric.lots()) {
        String code = FabricStockDemoPlan.lotCode(fabric, lot);
        assertThat(lot.rolls().stream().anyMatch(length -> length != FabricStockDemoPlan.ROLL_M))
            .as("%s ends with a roll that is not 120 m", code)
            .isTrue();
        assertThat(lot.rolls().stream().filter(length -> length == 90 || length == 135).count())
            .as("%s has at most one 90 m or 135 m remainder", code)
            .isLessThanOrEqualTo(1L);
      }
    }
  }

  @Test
  void coloursAreCardsTheColourDemoCreates() {
    Set<String> cards = new HashSet<>();
    SalesQuoteDemoSeeder.COLOUR_SPECS.stream().map(ColorCardSpec::code).forEach(cards::add);
    for (Lot lot : allLots()) {
      assertThat(cards).contains(lot.colour());
    }
  }

  @Test
  void lotCodesAreUniqueAndFitTheColumns() {
    Set<String> codes = new HashSet<>();
    for (Fabric fabric : FabricStockDemoPlan.FABRICS) {
      for (Lot lot : fabric.lots()) {
        String code = FabricStockDemoPlan.lotCode(fabric, lot);
        assertThat(codes.add(code)).as("duplicate lot code %s", code).isTrue();
        // batch_code and barcode are VARCHAR(100); a roll barcode adds "-NN" (or more digits).
        assertThat(code.length() + 4 + String.valueOf(lot.rolls().size()).length())
            .isLessThanOrEqualTo(100);
        assertThat(lot.rolls().size()).as("two-digit roll numbers are enough").isLessThan(100);
      }
    }
  }

  @Test
  void anUnconfirmedLotSitsNextToAtLeastTwoConfirmedOnes() {
    long unconfirmed = 0;
    for (Fabric fabric : FabricStockDemoPlan.FABRICS) {
      for (Lot lot : fabric.lots()) {
        if (lot.mixable()) continue;
        unconfirmed++;
        long confirmed =
            FabricStockDemoPlan.lotsOf(fabric, lot.colour(), lot.widthCm()).stream()
                .filter(Lot::mixable)
                .count();
        assertThat(confirmed)
            .as("%s has confirmed siblings", FabricStockDemoPlan.lotCode(fabric, lot))
            .isGreaterThanOrEqualTo(2);
      }
    }
    assertThat(unconfirmed).as("some lots are left unconfirmed on purpose").isPositive();
  }

  @Test
  void aRollWeighsLengthTimesWidthTimesGsm() {
    assertThat(FabricStockDemoSeeder.weightKg(new BigDecimal("120"), 150, 120))
        .isEqualByComparingTo("21.600");
    assertThat(FabricStockDemoSeeder.weightKg(new BigDecimal("135"), 160, 220))
        .isEqualByComparingTo("47.520");
  }
}
