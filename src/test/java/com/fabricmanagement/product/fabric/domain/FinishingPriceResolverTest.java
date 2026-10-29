package com.fabricmanagement.product.fabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPrice;
import com.fabricmanagement.product.fabric.domain.pricing.FinishingPriceResolver;
import com.fabricmanagement.product.fabric.domain.pricing.PriceContext;
import com.fabricmanagement.product.fabric.domain.pricing.PriceScope;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.domain.vocabulary.GsmBasis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * FABRIC-3c: which finishing prices are in force. A firm prices in its own way, so the answer is a
 * price per quality grade, taken from the firm's own tariff when it has one that fits the lot.
 */
class FinishingPriceResolverTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID PROCESS = UUID.randomUUID();
  private static final UUID YORKSHIRE = UUID.randomUUID();
  private static final UUID CALDER = UUID.randomUUID();
  private static final UUID DARK = UUID.randomUUID();
  private static final UUID BLACK = UUID.randomUUID();
  private static final UUID FIRST = UUID.randomUUID();
  private static final UUID SECOND = UUID.randomUUID();
  private static final LocalDate NOW = LocalDate.of(2026, 10, 20);

  private final List<FabricFinishingPrice> list = new ArrayList<>();

  private FabricFinishingPrice add(PriceScope scope, String amount, String from) {
    return add(scope, FinishingChargeUnit.M, amount, from);
  }

  private FabricFinishingPrice add(
      PriceScope scope, FinishingChargeUnit unit, String amount, String from) {
    FabricFinishingPrice price =
        FabricFinishingPrice.record(
            TENANT, PROCESS, scope, unit, new BigDecimal(amount), "GBP", LocalDate.parse(from));
    ReflectionTestUtils.setField(price, "id", UUID.randomUUID());
    list.add(price);
    return price;
  }

  private static PriceScope general() {
    return PriceScope.plain(null);
  }

  private static PriceScope firm() {
    return PriceScope.plain(YORKSHIRE);
  }

  private static PriceScope of(
      UUID partner, UUID grade, UUID priceClass, FabricFormation formation) {
    return new PriceScope(partner, null, grade, priceClass, formation, null, null, null);
  }

  private static PriceScope weighed(UUID partner, String from, String to, GsmBasis basis) {
    return new PriceScope(
        partner,
        null,
        null,
        null,
        null,
        from == null ? null : new BigDecimal(from),
        to == null ? null : new BigDecimal(to),
        basis);
  }

  private List<FabricFinishingPrice> resolve(UUID partner, String on, PriceContext context) {
    return FinishingPriceResolver.resolve(
        list, FinishingChargeUnit.M, partner, LocalDate.parse(on), context);
  }

  private List<FabricFinishingPrice> resolve(UUID partner, String on) {
    return resolve(partner, on, PriceContext.unknown());
  }

  // ---------------------------------------------------------------- firm or general

  @Test
  void aFirmsOwnTariffBeatsTheGeneralPriceEvenWhenTheGeneralOneIsNewer() {
    FabricFinishingPrice tariff = add(firm(), "1.60", "2026-01-01");
    add(general(), "2.10", "2026-06-01");

    assertThat(resolve(YORKSHIRE, "2026-10-20")).containsExactly(tariff);
  }

  @Test
  void aFirmWithoutATariffGetsTheGeneralPrice() {
    add(PriceScope.plain(CALDER), "1.40", "2026-01-01");
    FabricFinishingPrice general = add(general(), "2.10", "2026-01-01");

    assertThat(resolve(YORKSHIRE, "2026-10-20")).containsExactly(general);
  }

  @Test
  void workAtAnOwnSiteGetsTheGeneralPriceAndNeverAFirmsTariff() {
    add(firm(), "1.60", "2026-01-01");
    FabricFinishingPrice general = add(general(), "2.10", "2026-01-01");

    assertThat(resolve(null, "2026-10-20")).containsExactly(general);
  }

  @Test
  void aFirmsTariffIsTheWholeTariffSoTheGeneralPricesAreNotMixedIn() {
    FabricFinishingPrice firmFirst = add(of(YORKSHIRE, FIRST, null, null), "1.60", "2026-01-01");
    add(of(null, SECOND, null, null), "0.90", "2026-01-01");
    add(general(), "2.10", "2026-01-01");

    assertThat(resolve(YORKSHIRE, "2026-10-20"))
        .as("the firm bills the first grade only; the general second-grade price is not borrowed")
        .containsExactly(firmFirst);
  }

  @Test
  void aFirmThatOnlyPricesOtherLotsFallsBackToTheGeneralPrice() {
    add(of(YORKSHIRE, null, BLACK, null), "2.50", "2026-01-01");
    FabricFinishingPrice general = add(general(), "2.10", "2026-01-01");

    assertThat(resolve(YORKSHIRE, "2026-10-20", new PriceContext(DARK, null, null, null)))
        .containsExactly(general);
  }

  // ---------------------------------------------------------------- dates and units

  @Test
  void theLatestPriceThatHasStartedWinsAndAFutureOneWaits() {
    add(general(), "1.80", "2026-01-01");
    FabricFinishingPrice raised = add(general(), "1.95", "2026-09-01");
    add(general(), "2.20", "2026-12-01");

    assertThat(resolve(null, "2026-10-20")).containsExactly(raised);
    assertThat(resolve(null, "2026-09-01")).containsExactly(raised);
    assertThat(resolve(null, "2026-08-31"))
        .singleElement()
        .satisfies(price -> assertThat(price.getUnitPrice()).isEqualByComparingTo("1.80"));
  }

  @Test
  void nothingIsInForceBeforeTheFirstPrice() {
    add(general(), "1.80", "2026-06-01");

    assertThat(resolve(null, "2026-05-31")).isEmpty();
  }

  @Test
  void aPriceInTheProcessesOldUnitIsNotReadAsAPriceInItsNewUnit() {
    add(general(), FinishingChargeUnit.KG, "4.00", "2026-01-01");

    assertThat(resolve(null, "2026-10-20")).isEmpty();
  }

  // ---------------------------------------------------------------- quality grades

  @Test
  void eachQualityGradeGetsItsOwnPriceAndTheUngradedPriceComesFirst() {
    FabricFinishingPrice everyGrade = add(general(), "2.00", "2026-01-01");
    FabricFinishingPrice first = add(of(null, FIRST, null, null), "2.10", "2026-01-01");
    FabricFinishingPrice second = add(of(null, SECOND, null, null), "0.00", "2026-01-01");

    List<FabricFinishingPrice> prices = resolve(null, "2026-10-20");

    assertThat(prices).hasSize(3).containsExactlyInAnyOrder(everyGrade, first, second);
    assertThat(prices.get(0)).isSameAs(everyGrade);
  }

  @Test
  void aGradePriceHistoryIsKeptPerGrade() {
    add(of(null, FIRST, null, null), "2.00", "2026-01-01");
    FabricFinishingPrice firstNow = add(of(null, FIRST, null, null), "2.20", "2026-09-01");
    FabricFinishingPrice secondOnly = add(of(null, SECOND, null, null), "1.00", "2026-02-01");

    assertThat(resolve(null, "2026-10-20")).containsExactlyInAnyOrder(firstNow, secondOnly);
  }

  // ---------------------------------------------------------------- narrowing

  @Test
  void theMostSpecificPriceBeatsAMoreGeneralOneEvenIfTheGeneralOneIsNewer() {
    FabricFinishingPrice black = add(of(YORKSHIRE, null, BLACK, null), "2.60", "2026-01-01");
    add(firm(), "1.90", "2026-06-01");

    assertThat(resolve(YORKSHIRE, "2026-10-20", new PriceContext(BLACK, null, null, null)))
        .containsExactly(black);
    assertThat(resolve(YORKSHIRE, "2026-10-20", new PriceContext(DARK, null, null, null)))
        .singleElement()
        .satisfies(price -> assertThat(price.getUnitPrice()).isEqualByComparingTo("1.90"));
  }

  @Test
  void morePinnedDimensionsMeanMoreSpecific() {
    add(of(null, null, DARK, null), "2.00", "2026-01-01");
    FabricFinishingPrice darkKnit =
        add(of(null, null, DARK, FabricFormation.KNITTED), "2.30", "2026-01-01");

    assertThat(
            resolve(
                null, "2026-10-20", new PriceContext(DARK, FabricFormation.KNITTED, null, null)))
        .containsExactly(darkKnit);
    assertThat(
            resolve(null, "2026-10-20", new PriceContext(DARK, FabricFormation.WOVEN, null, null)))
        .singleElement()
        .satisfies(price -> assertThat(price.getUnitPrice()).isEqualByComparingTo("2.00"));
  }

  @Test
  void aWeightBandPicksTheBandTheLotFallsIn() {
    FabricFinishingPrice light =
        add(weighed(null, null, "150", GsmBasis.GREIGE), "1.50", "2026-01-01");
    FabricFinishingPrice heavy =
        add(weighed(null, "150.1", null, GsmBasis.GREIGE), "1.90", "2026-01-01");

    assertThat(
            resolve(null, "2026-10-20", new PriceContext(null, null, new BigDecimal("120"), null)))
        .containsExactly(light);
    assertThat(
            resolve(null, "2026-10-20", new PriceContext(null, null, new BigDecimal("210"), null)))
        .containsExactly(heavy);
  }

  @Test
  void aWeightBandDoesNotApplyToALotWhoseWeightIsUnknown() {
    FabricFinishingPrice anyWeight = add(general(), "1.70", "2026-01-01");
    add(weighed(null, "100", "200", GsmBasis.GREIGE), "1.50", "2026-01-01");

    assertThat(resolve(null, "2026-10-20")).containsExactly(anyWeight);
  }

  @Test
  void whenRowsAreEquallySpecificTheLatestStartedWins() {
    add(of(null, null, DARK, null), "2.00", "2026-01-01");
    FabricFinishingPrice newer = add(of(null, null, DARK, null), "2.10", "2026-05-01");

    assertThat(resolve(null, "2026-10-20", new PriceContext(DARK, null, null, null)))
        .containsExactly(newer);
  }

  @Test
  void theBillingBasisRidesAlongOnTheWinner() {
    FabricFinishingPrice sent =
        add(
            new PriceScope(
                YORKSHIRE, FinishingBillingBasis.SENT, null, null, null, null, null, null),
            "1.60",
            "2026-01-01");

    assertThat(resolve(YORKSHIRE, "2026-10-20"))
        .singleElement()
        .isSameAs(sent)
        .extracting(FabricFinishingPrice::getBillingBasis)
        .isEqualTo(FinishingBillingBasis.SENT);
  }

  @Test
  void anEmptyListPricesNothing() {
    assertThat(resolve(YORKSHIRE, "2026-10-20")).isEmpty();
  }
}
