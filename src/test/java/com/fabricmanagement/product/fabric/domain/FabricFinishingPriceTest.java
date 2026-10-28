package com.fabricmanagement.product.fabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPrice;
import com.fabricmanagement.product.fabric.domain.pricing.PriceContext;
import com.fabricmanagement.product.fabric.domain.pricing.PriceScope;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.domain.vocabulary.GsmBasis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FABRIC-3c: a finishing price is an amount per metre or kilogram from a date. */
class FabricFinishingPriceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID PROCESS = UUID.randomUUID();
  private static final UUID FIRM = UUID.randomUUID();
  private static final LocalDate FROM = LocalDate.of(2026, 10, 1);

  private static FabricFinishingPrice price(String amount, String currency, LocalDate from) {
    return FabricFinishingPrice.record(
        TENANT,
        PROCESS,
        PriceScope.plain(FIRM),
        FinishingChargeUnit.M,
        amount == null ? null : new BigDecimal(amount),
        currency,
        from);
  }

  @Test
  void aPriceKeepsItsAmountAtFourDecimalPlaces() {
    FabricFinishingPrice price = price("1.85", "GBP", FROM);

    assertThat(price.getUnitPrice()).isEqualByComparingTo("1.85");
    assertThat(price.getUnitPrice().scale()).isEqualTo(4);
    assertThat(price.getUnit()).isEqualTo(FinishingChargeUnit.M);
    assertThat(price.getCurrency()).isEqualTo("GBP");
    assertThat(price.getValidFrom()).isEqualTo(FROM);
  }

  @Test
  void aNoChargePriceIsAllowedBecauseSomeFirmsDoNotChargeForSomeSteps() {
    assertThat(price("0", "GBP", FROM).getUnitPrice()).isEqualByComparingTo("0");
  }

  @Test
  void trailingZerosBeyondTheLimitAreNotCountedAgainstTheAmount() {
    assertThat(price("2.850000", "GBP", FROM).getUnitPrice()).isEqualByComparingTo("2.85");
    assertThat(price("99999999999.9999", "GBP", FROM).getUnitPrice())
        .isEqualByComparingTo("99999999999.9999");
  }

  @Test
  void anAmountBelowZeroOrWithTooManyDigitsIsRefused() {
    assertThatThrownBy(() -> price("-0.01", "GBP", FROM)).isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> price(null, "GBP", FROM)).isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> price("1.23456", "GBP", FROM))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> price("100000000000", "GBP", FROM))
        .isInstanceOf(FabricDomainException.class);
  }

  @Test
  void aCurrencyIsAThreeLetterUpperCaseCode() {
    assertThatThrownBy(() -> price("1", "gbp", FROM)).isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> price("1", "POUND", FROM)).isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> price("1", null, FROM)).isInstanceOf(FabricDomainException.class);
  }

  @Test
  void aPriceNeedsATenantAProcessAUnitAndADate() {
    assertThatThrownBy(
            () ->
                FabricFinishingPrice.record(
                    null,
                    PROCESS,
                    PriceScope.plain(FIRM),
                    FinishingChargeUnit.M,
                    BigDecimal.ONE,
                    "GBP",
                    FROM))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(
            () ->
                FabricFinishingPrice.record(
                    TENANT,
                    null,
                    PriceScope.plain(FIRM),
                    FinishingChargeUnit.M,
                    BigDecimal.ONE,
                    "GBP",
                    FROM))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(
            () ->
                FabricFinishingPrice.record(
                    TENANT, PROCESS, PriceScope.plain(FIRM), null, BigDecimal.ONE, "GBP", FROM))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(
            () ->
                FabricFinishingPrice.record(
                    TENANT, PROCESS, null, FinishingChargeUnit.M, BigDecimal.ONE, "GBP", FROM))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> price("1", "GBP", null)).isInstanceOf(FabricDomainException.class);
  }

  @Test
  void aPriceAppliesFromItsDateOnward() {
    FabricFinishingPrice price = price("1", "GBP", FROM);

    assertThat(price.startedBy(FROM.minusDays(1))).isFalse();
    assertThat(price.startedBy(FROM)).isTrue();
    assertThat(price.startedBy(FROM.plusYears(1))).isTrue();
  }

  @Test
  void aPriceWithoutAFirmIsTheGeneralPrice() {
    FabricFinishingPrice general =
        FabricFinishingPrice.record(
            TENANT,
            PROCESS,
            PriceScope.plain(null),
            FinishingChargeUnit.KG,
            BigDecimal.ONE,
            "GBP",
            FROM);

    assertThat(general.isFirmTariff()).isFalse();
    assertThat(price("1", "GBP", FROM).isFirmTariff()).isTrue();
  }

  @Test
  void withdrawingAPriceMarksItDeleted() {
    FabricFinishingPrice price = price("1", "GBP", FROM);

    price.withdraw();

    assertThat(price.getDeletedAt()).isNotNull();
    assertThat(price.getIsActive()).isFalse();
  }

  // ---------------------------------------------------------------- narrowing

  private static final UUID DARK = UUID.randomUUID();
  private static final UUID FIRST_GRADE = UUID.randomUUID();

  private static FabricFinishingPrice priced(PriceScope scope) {
    return FabricFinishingPrice.record(
        TENANT, PROCESS, scope, FinishingChargeUnit.M, new BigDecimal("1.85"), "GBP", FROM);
  }

  private static PriceScope scope(
      UUID grade,
      UUID priceClass,
      FabricFormation formation,
      String gsmFrom,
      String gsmTo,
      GsmBasis basis) {
    return new PriceScope(
        FIRM,
        null,
        grade,
        priceClass,
        formation,
        gsmFrom == null ? null : new BigDecimal(gsmFrom),
        gsmTo == null ? null : new BigDecimal(gsmTo),
        basis);
  }

  private static PriceContext lot(UUID priceClass, FabricFormation formation, String greige) {
    return new PriceContext(
        priceClass, formation, greige == null ? null : new BigDecimal(greige), null);
  }

  @Test
  void aPriceRemembersWhatItIsNarrowedBy() {
    FabricFinishingPrice price =
        priced(
            new PriceScope(
                FIRM,
                FinishingBillingBasis.SENT,
                FIRST_GRADE,
                DARK,
                FabricFormation.KNITTED,
                new BigDecimal("150"),
                new BigDecimal("200.5"),
                GsmBasis.GREIGE));

    assertThat(price.getBillingBasis()).isEqualTo(FinishingBillingBasis.SENT);
    assertThat(price.getQualityGradeId()).isEqualTo(FIRST_GRADE);
    assertThat(price.getPriceClassId()).isEqualTo(DARK);
    assertThat(price.getFormation()).isEqualTo(FabricFormation.KNITTED);
    assertThat(price.getGsmFrom()).isEqualByComparingTo("150");
    assertThat(price.getGsmFrom().scale()).isEqualTo(1);
    assertThat(price.getGsmTo()).isEqualByComparingTo("200.5");
    assertThat(price.getGsmBasis()).isEqualTo(GsmBasis.GREIGE);
    assertThat(price.scope().qualityGradeId()).isEqualTo(FIRST_GRADE);
  }

  @Test
  void aPriceIsForWhatCameBackUnlessSaidOtherwise() {
    assertThat(priced(PriceScope.plain(FIRM)).getBillingBasis())
        .isEqualTo(FinishingBillingBasis.RETURNED);
  }

  @Test
  void specificityCountsClassFormationAndWeightRangeButNotTheGrade() {
    assertThat(priced(PriceScope.plain(FIRM)).specificity()).isZero();
    assertThat(priced(scope(FIRST_GRADE, null, null, null, null, null)).specificity()).isZero();
    assertThat(priced(scope(null, DARK, null, null, null, null)).specificity()).isEqualTo(1);
    assertThat(priced(scope(null, DARK, FabricFormation.WOVEN, null, null, null)).specificity())
        .isEqualTo(2);
    assertThat(
            priced(scope(null, DARK, FabricFormation.WOVEN, "100", null, GsmBasis.FINISHED))
                .specificity())
        .isEqualTo(3);
  }

  @Test
  void aPriceWithoutNarrowingFitsAnyLotEvenOneNothingIsKnownAbout() {
    FabricFinishingPrice plain = priced(PriceScope.plain(FIRM));

    assertThat(plain.matches(PriceContext.unknown())).isTrue();
    assertThat(plain.matches(lot(DARK, FabricFormation.KNITTED, "180"))).isTrue();
  }

  @Test
  void aPriceClassAndAFormationMustBeTheLots() {
    FabricFinishingPrice dark = priced(scope(null, DARK, null, null, null, null));
    FabricFinishingPrice woven = priced(scope(null, null, FabricFormation.WOVEN, null, null, null));

    assertThat(dark.matches(lot(DARK, null, null))).isTrue();
    assertThat(dark.matches(lot(UUID.randomUUID(), null, null))).isFalse();
    assertThat(dark.matches(PriceContext.unknown())).isFalse();
    assertThat(woven.matches(lot(null, FabricFormation.WOVEN, null))).isTrue();
    assertThat(woven.matches(lot(null, FabricFormation.KNITTED, null))).isFalse();
    assertThat(woven.matches(PriceContext.unknown())).isFalse();
  }

  @Test
  void aWeightRangeIsInclusiveAtBothEndsAndEitherEndMayBeOpen() {
    FabricFinishingPrice band = priced(scope(null, null, null, "150", "200", GsmBasis.GREIGE));
    FabricFinishingPrice upTo150 = priced(scope(null, null, null, null, "150", GsmBasis.GREIGE));
    FabricFinishingPrice from200 = priced(scope(null, null, null, "200", null, GsmBasis.GREIGE));

    assertThat(band.matches(lot(null, null, "149.9"))).isFalse();
    assertThat(band.matches(lot(null, null, "150"))).isTrue();
    assertThat(band.matches(lot(null, null, "200"))).isTrue();
    assertThat(band.matches(lot(null, null, "200.1"))).isFalse();
    assertThat(upTo150.matches(lot(null, null, "40"))).isTrue();
    assertThat(upTo150.matches(lot(null, null, "150.1"))).isFalse();
    assertThat(from200.matches(lot(null, null, "200"))).isTrue();
    assertThat(from200.matches(lot(null, null, "199.9"))).isFalse();
  }

  @Test
  void aWeightRangeIsReadAgainstTheWeightItNames() {
    FabricFinishingPrice finished =
        priced(scope(null, null, null, "150", "200", GsmBasis.FINISHED));
    PriceContext greigeOnly = new PriceContext(null, null, new BigDecimal("180"), null);
    PriceContext finishedToo =
        new PriceContext(null, null, new BigDecimal("120"), new BigDecimal("180"));

    assertThat(finished.matches(greigeOnly)).as("the lot's finished weight is unknown").isFalse();
    assertThat(finished.matches(finishedToo)).isTrue();
  }

  @Test
  void aWeightRangeNeedsItsBasisAndTheBasisNeedsARange() {
    assertThatThrownBy(() -> scope(null, null, null, "150", "200", null))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> scope(null, null, null, null, null, GsmBasis.GREIGE))
        .isInstanceOf(FabricDomainException.class);
  }

  @Test
  void aWeightRangeMustStartBelowItsEndAndKeepToOneDecimal() {
    assertThatThrownBy(() -> scope(null, null, null, "200", "150", GsmBasis.GREIGE))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> scope(null, null, null, "150", "150", GsmBasis.GREIGE))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> scope(null, null, null, "-1", "150", GsmBasis.GREIGE))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> scope(null, null, null, null, "0", GsmBasis.GREIGE))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> scope(null, null, null, "150.25", "200", GsmBasis.GREIGE))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(() -> scope(null, null, null, "1000000", null, GsmBasis.GREIGE))
        .isInstanceOf(FabricDomainException.class);
  }

  @Test
  void twoPricesAreInTheSameSlotWhenEveryNarrowingAndTheDateAgree() {
    FabricFinishingPrice price =
        priced(scope(FIRST_GRADE, DARK, FabricFormation.WOVEN, "150", "200", GsmBasis.GREIGE));

    assertThat(
            price.sameSlotAs(
                scope(FIRST_GRADE, DARK, FabricFormation.WOVEN, "150.0", "200", GsmBasis.GREIGE),
                FROM))
        .isTrue();
    assertThat(
            price.sameSlotAs(
                scope(FIRST_GRADE, DARK, FabricFormation.WOVEN, "150", "200", GsmBasis.GREIGE),
                FROM.plusDays(1)))
        .isFalse();
    assertThat(
            price.sameSlotAs(
                scope(null, DARK, FabricFormation.WOVEN, "150", "200", GsmBasis.GREIGE), FROM))
        .as("another grade")
        .isFalse();
    assertThat(
            price.sameSlotAs(
                scope(FIRST_GRADE, DARK, FabricFormation.WOVEN, "150", "200", GsmBasis.FINISHED),
                FROM))
        .as("another weight basis")
        .isFalse();
    assertThat(price.sameSlotAs(PriceScope.plain(FIRM), FROM)).isFalse();
  }

  @Test
  void theBillingBasisIsNotPartOfTheSlot() {
    FabricFinishingPrice returned = priced(PriceScope.plain(FIRM));

    assertThat(
            returned.sameSlotAs(
                new PriceScope(
                    FIRM, FinishingBillingBasis.SENT, null, null, null, null, null, null),
                FROM))
        .isTrue();
  }
}
