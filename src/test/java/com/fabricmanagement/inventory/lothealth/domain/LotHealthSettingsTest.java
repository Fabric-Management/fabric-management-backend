package com.fabricmanagement.inventory.lothealth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LotHealthSettingsTest {

  @Test
  void theStandardIsThreeFiveEight_andFiftyOnePercent() {
    LotHealthSettings standard = LotHealthSettings.STANDARD;

    assertThat(standard.policyFor(ProductSpeed.MOVING))
        .isEqualTo(new LotHealthPolicy(3, new BigDecimal("0.51")));
    assertThat(standard.policyFor(ProductSpeed.SLOWING).minHealthyPieces()).isEqualTo(5);
    assertThat(standard.policyFor(ProductSpeed.STALLED).minHealthyPieces()).isEqualTo(8);
    assertThat(standard.policyFor(ProductSpeed.MOVING)).isEqualTo(LotHealthPolicy.STANDARD);
    assertThat(standard.fabricRollPolicy())
        .isEqualTo(new LotHealthPolicy(4, new BigDecimal("0.51")));
    assertThat(
            new LotHealthSettings(3, 5, 8, new BigDecimal("0.51"), 9)
                .fabricRollPolicy()
                .minHealthyPieces())
        .isEqualTo(9);
    assertThatThrownBy(() -> new LotHealthSettings(3, 5, 8, new BigDecimal("0.51"), 3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void minimumsMustRiseWithSlowerSales() {
    BigDecimal ratio = new BigDecimal("0.51");
    assertThatThrownBy(() -> new LotHealthSettings(5, 3, 8, ratio))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LotHealthSettings(3, 5, 4, ratio))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LotHealthSettings(0, 5, 8, ratio))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new LotHealthSettings(3, 3, 3, ratio).policyFor(ProductSpeed.STALLED))
        .isEqualTo(new LotHealthPolicy(3, ratio));
  }
}
