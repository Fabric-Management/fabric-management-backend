package com.fabricmanagement.production.core.batch.domain.attributes;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YarnAttributesTest {

  @Test
  void roundTripUsesBigDecimalsAndWritesOnlyCanonicalTwistKey() {
    YarnAttributes attributes =
        new YarnAttributes(
            "30/1",
            "Z",
            "RING",
            1,
            "100% Cotton",
            new BigDecimal("728.35"),
            new BigDecimal("2800.50"));

    Map<String, Object> serialized = attributes.toMap();

    assertThat(YarnAttributes.from(serialized)).isEqualTo(attributes);
    assertThat(serialized)
        .containsEntry("yarn_twist_tpm", new BigDecimal("728.35"))
        .doesNotContainKey("yarn_tpi");
  }

  @Test
  void legacyTpiReadDerivesTpmFromTheNamedConversionPolicy() {
    BigDecimal legacyTpi = new BigDecimal("18.5");
    BigDecimal expected =
        legacyTpi
            .multiply(TwistConversion.TPI_TO_TPM_FACTOR)
            .setScale(TwistConversion.OUTPUT_SCALE, TwistConversion.ROUNDING_MODE);

    YarnAttributes attributes = YarnAttributes.from(Map.of("yarn_tpi", legacyTpi));

    assertThat(TwistConversion.POLICY_NAME).isEqualTo("twist-v1");
    assertThat(attributes.turnsPerMeter())
        .isEqualByComparingTo(expected)
        .isEqualByComparingTo("728.35");
  }

  @Test
  void canonicalTpmTakesPrecedenceWhenBothKeysExist() {
    Map<String, Object> raw =
        Map.of(
            "yarn_twist_tpm", "700.25",
            "yarn_tpi", 18.5,
            "yarn_csp", "2800.50");

    YarnAttributes attributes = YarnAttributes.from(raw);

    assertThat(attributes.turnsPerMeter()).isEqualByComparingTo("700.25");
    assertThat(attributes.csp()).isEqualByComparingTo("2800.50");
  }
}
