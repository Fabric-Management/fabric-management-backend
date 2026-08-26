package com.fabricmanagement.product.core.domain.registry;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.core.domain.registry.policy.LinearDensityV1;
import com.fabricmanagement.product.core.domain.registry.policy.PolicyRegistry;
import com.fabricmanagement.product.core.domain.registry.policy.ResolverPolicyNames;
import com.fabricmanagement.product.core.domain.registry.policy.Tex2dp;
import com.fabricmanagement.product.core.domain.registry.policy.Tpm2dp;
import com.fabricmanagement.product.core.domain.registry.policy.TwistV1;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PropertyDefinitionValidatorTest {

  private PropertyDefinitionValidator validator;

  @BeforeEach
  void setUp() {
    validator =
        new PropertyDefinitionValidator(
            new PolicyRegistry(
                List.of(TwistV1.INSTANCE, LinearDensityV1.INSTANCE),
                List.of(Tex2dp.INSTANCE, Tpm2dp.INSTANCE)));
  }

  @Test
  void rule1RejectsUnknownPolicyNames() {
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.TWIST_DENSITY,
            UnitCode.TPM,
            List.of(UnitCode.TPM, UnitCode.TPI),
            "twist-v9",
            Tpm2dp.POLICY_NAME,
            knownNominal(),
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 1");
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.TWIST_DENSITY,
            UnitCode.TPM,
            List.of(UnitCode.TPM, UnitCode.TPI),
            TwistV1.POLICY_NAME,
            "tpm-9dp",
            knownNominal(),
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 1");
  }

  @Test
  void rule2RejectsDuplicateKeys() {
    PropertyDefinitionSpec definition = validTwist();
    assertThatThrownBy(() -> validator.validateUniqueKeys(List.of(definition, definition)))
        .isInstanceOf(PropertyRegistryException.class)
        .hasMessageContaining("rule 2");
  }

  @Test
  void rule3RejectsCanonicalUnitOutsideAllowedUnits() {
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.TWIST_DENSITY,
            UnitCode.TPM,
            List.of(UnitCode.TPI),
            TwistV1.POLICY_NAME,
            Tpm2dp.POLICY_NAME,
            knownNominal(),
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 3");
  }

  @Test
  void rule4RejectsAllowedUnitFromAnotherFamily() {
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.TWIST_DENSITY,
            UnitCode.TPM,
            List.of(UnitCode.TPM, UnitCode.TEX),
            TwistV1.POLICY_NAME,
            Tpm2dp.POLICY_NAME,
            knownNominal(),
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 4");
  }

  @Test
  void rule5RejectsConversionFromAnotherFamily() {
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.LINEAR_DENSITY,
            UnitCode.TEX,
            List.of(UnitCode.TEX, UnitCode.DTEX),
            TwistV1.POLICY_NAME,
            Tex2dp.POLICY_NAME,
            knownNominal(),
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 5");
  }

  @Test
  void rule6RejectsRoundingFromAnotherFamily() {
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.TWIST_DENSITY,
            UnitCode.TPM,
            List.of(UnitCode.TPM, UnitCode.TPI),
            TwistV1.POLICY_NAME,
            Tex2dp.POLICY_NAME,
            knownNominal(),
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 6");
  }

  @Test
  void rule7RejectsWiderUnitSetWithoutConversion() {
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.TWIST_DENSITY,
            UnitCode.TPM,
            List.of(UnitCode.TPM, UnitCode.TPI),
            null,
            Tpm2dp.POLICY_NAME,
            knownNominal(),
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 7");
  }

  @Test
  void rule8RejectsUnitsForNoneAndNonNumericUnitBearingProperties() {
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.NONE,
            UnitCode.PCT,
            List.of(UnitCode.PCT),
            null,
            null,
            knownNominal(),
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 8");
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.PERCENTAGE,
            UnitCode.PCT,
            List.of(UnitCode.PCT),
            null,
            null,
            knownNominal(),
            knownTolerance(),
            PropertyDataType.STRING),
        "rule 8");
  }

  @Test
  void rule9RejectsDuplicateAllowedUnitsAndOwnershipPrefixMismatch() {
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.TWIST_DENSITY,
            UnitCode.TPM,
            List.of(UnitCode.TPM, UnitCode.TPM),
            TwistV1.POLICY_NAME,
            Tpm2dp.POLICY_NAME,
            knownNominal(),
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 9");
    assertViolation(
        definition(
            "CUSTOM_TENANT_TEST",
            true,
            UnitFamily.NONE,
            null,
            List.of(),
            null,
            null,
            knownNominal(),
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 9");
    assertViolation(
        definition(
            "TENANT_TEST",
            false,
            UnitFamily.NONE,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            PropertyDataType.DECIMAL),
        "rule 9");
  }

  @Test
  void rule10aRejectsUnknownResolver() {
    assertViolation(
        definition(
            "CUSTOM_TEST",
            false,
            UnitFamily.NONE,
            null,
            List.of(),
            null,
            null,
            "resolveLater",
            null,
            PropertyDataType.DECIMAL),
        "rule 10");
  }

  @Test
  void rule10bRequiresBothResolversForSystemRows() {
    assertViolation(
        definition(
            "YARN_TEST",
            true,
            UnitFamily.NONE,
            null,
            List.of(),
            null,
            null,
            null,
            knownTolerance(),
            PropertyDataType.DECIMAL),
        "rule 10");
  }

  @Test
  void rule10cAllowsCustomRowsWithBothResolversNull() {
    assertThatCode(
            () ->
                validator.validate(
                    definition(
                        "CUSTOM_TEST",
                        false,
                        UnitFamily.NONE,
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        PropertyDataType.DECIMAL)))
        .doesNotThrowAnyException();
  }

  private void assertViolation(PropertyDefinitionSpec definition, String rule) {
    assertThatThrownBy(() -> validator.validate(definition))
        .isInstanceOf(PropertyRegistryException.class)
        .hasMessageContaining(definition.propertyKey())
        .hasMessageContaining(rule);
  }

  private static PropertyDefinitionSpec validTwist() {
    return definition(
        "YARN_TEST",
        true,
        UnitFamily.TWIST_DENSITY,
        UnitCode.TPM,
        List.of(UnitCode.TPM, UnitCode.TPI),
        TwistV1.POLICY_NAME,
        Tpm2dp.POLICY_NAME,
        knownNominal(),
        knownTolerance(),
        PropertyDataType.DECIMAL);
  }

  private static PropertyDefinitionSpec definition(
      String key,
      boolean system,
      UnitFamily family,
      UnitCode canonical,
      List<UnitCode> allowed,
      String conversion,
      String rounding,
      String nominal,
      String tolerance,
      PropertyDataType dataType) {
    return new PropertyDefinitionSpec(
        key,
        "testField",
        SemanticRole.MEASUREMENT,
        "testDimension",
        dataType,
        family,
        canonical,
        allowed,
        conversion,
        rounding,
        nominal,
        tolerance,
        "Test property definition.",
        system);
  }

  private static String knownNominal() {
    return ResolverPolicyNames.YARN_LOT_NOMINAL_V1;
  }

  private static String knownTolerance() {
    return ResolverPolicyNames.QUALITY_ACCEPTANCE_PROFILE;
  }
}
