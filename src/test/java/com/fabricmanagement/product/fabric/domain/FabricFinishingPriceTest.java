package com.fabricmanagement.product.fabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPrice;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
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
        FIRM,
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
                    null, PROCESS, FIRM, FinishingChargeUnit.M, BigDecimal.ONE, "GBP", FROM))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(
            () ->
                FabricFinishingPrice.record(
                    TENANT, null, FIRM, FinishingChargeUnit.M, BigDecimal.ONE, "GBP", FROM))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(
            () ->
                FabricFinishingPrice.record(
                    TENANT, PROCESS, FIRM, null, BigDecimal.ONE, "GBP", FROM))
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
            TENANT, PROCESS, null, FinishingChargeUnit.KG, BigDecimal.ONE, "GBP", FROM);

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
}
