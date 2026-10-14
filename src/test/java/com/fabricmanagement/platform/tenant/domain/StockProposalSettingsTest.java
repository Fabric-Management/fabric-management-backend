package com.fabricmanagement.platform.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Stock-proposal settings in the tenant's settings JSON (Fatih, 2026-10-14). */
class StockProposalSettingsTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void theDefaultsAreTheDecidedValues() {
    StockProposalSettings defaults = StockProposalSettings.defaults();

    assertThat(defaults.toleranceDownPct()).isEqualByComparingTo("5");
    assertThat(defaults.toleranceUpPct()).isEqualByComparingTo("5");
    assertThat(defaults.minHealthyPiecesMoving()).isEqualTo(3);
    assertThat(defaults.minHealthyPiecesSlowing()).isEqualTo(5);
    assertThat(defaults.minHealthyPiecesStalled()).isEqualTo(8);
    assertThat(defaults.shortPiecePct()).isEqualByComparingTo("51");
    assertThat(defaults.speedWindowDays()).isEqualTo(90);
    assertThat(defaults.slowingBelowPct()).isEqualByComparingTo("80");
    assertThat(defaults.stalledBelowPct()).isEqualByComparingTo("40");
    assertThat(defaults.slowingCoverDays()).isEqualTo(180);
    assertThat(defaults.lotCloseExtraSlowingPct()).isEqualByComparingTo("10");
    assertThat(defaults.lotCloseExtraStalledPct()).isEqualByComparingTo("20");
  }

  @Test
  void settingsSavedBeforeTheGroupExisted_loadWithItsDefaults() throws Exception {
    TenantSettings settings =
        json.readValue(
            "{\"timezone\":\"UTC\",\"locale\":\"en-US\",\"currency\":\"USD\"}",
            TenantSettings.class);

    assertThat(settings.getStockProposal()).isNull();
    assertThat(settings.stockProposalOrDefaults()).isEqualTo(StockProposalSettings.defaults());
  }

  @Test
  void aGroupMissingALaterValue_loadsThatValueAsItsDefault() throws Exception {
    TenantSettings settings =
        json.readValue(
            "{\"stockProposal\":{\"toleranceDownPct\":3,\"minHealthyPiecesMoving\":4}}",
            TenantSettings.class);

    StockProposalSettings group = settings.stockProposalOrDefaults();
    assertThat(group.toleranceDownPct()).isEqualByComparingTo("3");
    assertThat(group.toleranceUpPct()).isEqualByComparingTo("5");
    assertThat(group.minHealthyPiecesMoving()).isEqualTo(4);
    assertThat(group.minHealthyPiecesSlowing()).isEqualTo(5);
  }

  @Test
  void theGroupRoundTripsThroughJson_andTheDefaultsAccessorIsNotStored() throws Exception {
    TenantSettings settings = TenantSettings.defaults();
    settings.setStockProposal(
        new StockProposalSettings(
            new BigDecimal("2"),
            new BigDecimal("4"),
            3,
            6,
            9,
            new BigDecimal("50"),
            60,
            new BigDecimal("70"),
            new BigDecimal("30"),
            120,
            new BigDecimal("5"),
            new BigDecimal("15")));

    String written = json.writeValueAsString(settings);
    TenantSettings read = json.readValue(written, TenantSettings.class);

    assertThat(read.getStockProposal()).isEqualTo(settings.getStockProposal());
    assertThat(written).doesNotContain("stockProposalOrDefaults");
  }
}
