package com.fabricmanagement.platform.tradingpartner.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PAYMENT-PLAN-7 R13: a name is shown tidied and compared case-insensitively. */
class TradingPartnerChequeDrawerTest {

  @Test
  void namesAreTidiedForDisplayAndComparedWithoutCase() {
    var drawer =
        TradingPartnerChequeDrawer.create(
            UUID.randomUUID(), "  Oliver   Hartley ", ChequeDrawerType.INDIVIDUAL);
    assertThat(drawer.getName()).isEqualTo("Oliver Hartley");
    assertThat(drawer.getNormalizedName()).isEqualTo("oliver hartley");
    assertThat(TradingPartnerChequeDrawer.normalise("OLIVER HARTLEY"))
        .isEqualTo(drawer.getNormalizedName());
    assertThat(drawer.isUsable()).isTrue();
  }

  @Test
  void dottedAndDotlessIAndPastedSpacesCompareAsOneName() {
    String key = TradingPartnerChequeDrawer.normalise("Isla Moor");
    assertThat(TradingPartnerChequeDrawer.normalise("\u0130SLA MOOR")).isEqualTo(key);
    assertThat(TradingPartnerChequeDrawer.normalise("\u0131sla moor")).isEqualTo(key);
    assertThat(TradingPartnerChequeDrawer.normalise("Isla\u00a0Moor")).isEqualTo(key);
    assertThat(TradingPartnerChequeDrawer.displayName(" Isla\u00a0\u2003Moor "))
        .isEqualTo("Isla Moor");
  }

  @Test
  void theKeyIsNeverLongerThanTheName() {
    String name = "\u0130".repeat(200);
    assertThat(TradingPartnerChequeDrawer.normalise(name)).hasSize(200);
  }

  @Test
  void deactivationKeepsTheRowAndReactivationReturnsIt() {
    var drawer =
        TradingPartnerChequeDrawer.create(
            UUID.randomUUID(), "Hartley Holdings", ChequeDrawerType.COMPANY);
    drawer.deactivate();
    assertThat(drawer.isUsable()).isFalse();
    assertThat(drawer.getName()).isEqualTo("Hartley Holdings");
    drawer.reactivate();
    assertThat(drawer.isUsable()).isTrue();
  }

  @Test
  void blankOrOverlongNamesAndAMissingTypeAreRefused() {
    UUID partner = UUID.randomUUID();
    assertThatThrownBy(
            () -> TradingPartnerChequeDrawer.create(partner, "   ", ChequeDrawerType.COMPANY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                TradingPartnerChequeDrawer.create(
                    partner, "x".repeat(201), ChequeDrawerType.COMPANY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TradingPartnerChequeDrawer.create(partner, "Ava Pryce", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
