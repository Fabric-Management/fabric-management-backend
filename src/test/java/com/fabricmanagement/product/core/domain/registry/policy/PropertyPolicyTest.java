package com.fabricmanagement.product.core.domain.registry.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.core.app.bootstrap.PropertyDefinitionSeeder;
import com.fabricmanagement.product.core.app.config.PropertyRegistryConfiguration;
import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import com.fabricmanagement.product.core.domain.registry.UnitCode;
import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class PropertyPolicyTest {

  @Test
  void goldenConversionsUseIndependentLiteralOracles() {
    assertThat(
            Tpm2dp.INSTANCE.apply(
                TwistV1.INSTANCE.toCanonical(new BigDecimal("18.5"), UnitCode.TPI)))
        .isEqualByComparingTo("728.35");
    assertThat(
            Tex2dp.INSTANCE.apply(
                LinearDensityV1.INSTANCE.toCanonical(new BigDecimal("30"), UnitCode.NE)))
        .isEqualByComparingTo("19.68");
    assertThat(
            Tex2dp.INSTANCE.apply(
                LinearDensityV1.INSTANCE.toCanonical(new BigDecimal("50"), UnitCode.NM)))
        .isEqualByComparingTo("20.00");
    assertThat(
            Tex2dp.INSTANCE.apply(
                LinearDensityV1.INSTANCE.fromCanonical(new BigDecimal("20"), UnitCode.DENIER)))
        .isEqualByComparingTo("180.00");
    assertThat(
            Tex2dp.INSTANCE.apply(
                LinearDensityV1.INSTANCE.fromCanonical(new BigDecimal("20"), UnitCode.DTEX)))
        .isEqualByComparingTo("200.00");

    BigDecimal input = new BigDecimal("31.25");
    BigDecimal roundTrip =
        LinearDensityV1.INSTANCE.fromCanonical(
            Tex2dp.INSTANCE.apply(LinearDensityV1.INSTANCE.toCanonical(input, UnitCode.NE)),
            UnitCode.NE);
    assertThat(roundTrip.subtract(input).abs()).isLessThan(new BigDecimal("0.01"));
  }

  @Test
  void compatibilityFacadeRepublishesPolicyConstants() {
    assertThat(TwistConversion.POLICY_NAME).isSameAs(TwistV1.POLICY_NAME);
    assertThat(TwistConversion.TPI_TO_TPM_FACTOR).isSameAs(TwistV1.TPI_TO_TPM_FACTOR);
    assertThat(TwistConversion.OUTPUT_SCALE).isEqualTo(Tpm2dp.OUTPUT_SCALE);
    assertThat(TwistConversion.ROUNDING_MODE).isSameAs(Tpm2dp.ROUNDING_MODE);
  }

  @Test
  void boundariesAreConsistent() {
    assertThat(TwistV1.INSTANCE.toCanonical(null, UnitCode.TPI)).isNull();
    assertThat(TwistV1.INSTANCE.fromCanonical(null, UnitCode.TPI)).isNull();
    assertThat(LinearDensityV1.INSTANCE.toCanonical(null, UnitCode.NE)).isNull();
    assertThat(LinearDensityV1.INSTANCE.fromCanonical(null, UnitCode.NE)).isNull();
    assertThat(Tex2dp.INSTANCE.apply(null)).isNull();
    assertThat(Tpm2dp.INSTANCE.apply(null)).isNull();
    assertThat(TwistV1.INSTANCE.toCanonical(BigDecimal.ZERO, UnitCode.TPM))
        .isEqualByComparingTo(BigDecimal.ZERO);

    assertThatThrownBy(() -> TwistV1.INSTANCE.toCanonical(new BigDecimal("-1"), UnitCode.TPI))
        .isInstanceOf(PropertyRegistryException.class);
    assertThatThrownBy(() -> TwistV1.INSTANCE.fromCanonical(new BigDecimal("-1"), UnitCode.TPI))
        .isInstanceOf(PropertyRegistryException.class);
    assertThatThrownBy(() -> LinearDensityV1.INSTANCE.toCanonical(BigDecimal.ZERO, UnitCode.NE))
        .isInstanceOf(PropertyRegistryException.class);
    assertThatThrownBy(
            () -> LinearDensityV1.INSTANCE.toCanonical(new BigDecimal("-1"), UnitCode.NM))
        .isInstanceOf(PropertyRegistryException.class);
    assertThatThrownBy(() -> TwistV1.INSTANCE.toCanonical(BigDecimal.ONE, UnitCode.TEX))
        .isInstanceOf(PropertyRegistryException.class);
    assertThatThrownBy(() -> LinearDensityV1.INSTANCE.fromCanonical(BigDecimal.ONE, UnitCode.TPM))
        .isInstanceOf(PropertyRegistryException.class);
  }

  @Test
  void familyMembershipIsDerivedFromUnitCode() {
    assertThat(UnitFamily.NONE.codes()).isEmpty();
    for (UnitCode code : UnitCode.values()) {
      assertThat(code.unitFamily().codes()).contains(code);
    }
  }

  @Test
  void springDiscoversUniquePoliciesAndEveryCatalogueReferenceResolves() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(PropertyRegistryConfiguration.class)) {
      List<ConversionPolicy> conversions =
          new ArrayList<>(context.getBeansOfType(ConversionPolicy.class).values());
      List<RoundingPolicy> roundings =
          new ArrayList<>(context.getBeansOfType(RoundingPolicy.class).values());
      PolicyRegistry registry = new PolicyRegistry(conversions, roundings);

      assertThat(context.getBean("twistV1")).isSameAs(TwistV1.INSTANCE);
      assertThat(context.getBean("linearDensityV1")).isSameAs(LinearDensityV1.INSTANCE);
      assertThat(context.getBean("tex2dp")).isSameAs(Tex2dp.INSTANCE);
      assertThat(context.getBean("tpm2dp")).isSameAs(Tpm2dp.INSTANCE);

      PropertyDefinitionSeeder.systemDefinitions()
          .forEach(
              definition -> {
                assertThat(definition.unitFamily().codes())
                    .containsAll(definition.allowedUnitCodes());
                if (definition.conversionPolicy() != null) {
                  assertThat(registry.conversion(definition.conversionPolicy())).isPresent();
                }
                if (definition.roundingPolicy() != null) {
                  assertThat(registry.rounding(definition.roundingPolicy())).isPresent();
                }
              });
    }
  }

  @Test
  void duplicateDiscoveredNamesFailFast() {
    ConversionPolicy duplicate =
        new ConversionPolicy() {
          @Override
          public String name() {
            return TwistV1.POLICY_NAME;
          }

          @Override
          public UnitFamily unitFamily() {
            return UnitFamily.TWIST_DENSITY;
          }

          @Override
          public BigDecimal toCanonical(BigDecimal value, UnitCode sourceUnit) {
            return value;
          }

          @Override
          public BigDecimal fromCanonical(BigDecimal value, UnitCode targetUnit) {
            return value;
          }
        };

    assertThatThrownBy(
            () ->
                new PolicyRegistry(
                    List.of(TwistV1.INSTANCE, duplicate),
                    List.of(Tex2dp.INSTANCE, Tpm2dp.INSTANCE)))
        .isInstanceOf(PropertyRegistryException.class)
        .hasMessageContaining("Duplicate conversion");
  }
}
