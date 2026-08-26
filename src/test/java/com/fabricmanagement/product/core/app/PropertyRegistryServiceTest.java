package com.fabricmanagement.product.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.app.bootstrap.PropertyDefinitionSeeder;
import com.fabricmanagement.product.core.domain.registry.PropertyDataType;
import com.fabricmanagement.product.core.domain.registry.PropertyDefinition;
import com.fabricmanagement.product.core.domain.registry.PropertyDefinitionSpec;
import com.fabricmanagement.product.core.domain.registry.PropertyDefinitionValidator;
import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import com.fabricmanagement.product.core.domain.registry.SemanticRole;
import com.fabricmanagement.product.core.domain.registry.UnitCode;
import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import com.fabricmanagement.product.core.domain.registry.policy.LinearDensityV1;
import com.fabricmanagement.product.core.domain.registry.policy.PolicyRegistry;
import com.fabricmanagement.product.core.domain.registry.policy.Tex2dp;
import com.fabricmanagement.product.core.domain.registry.policy.Tpm2dp;
import com.fabricmanagement.product.core.domain.registry.policy.TwistV1;
import com.fabricmanagement.product.core.infra.repository.PropertyDefinitionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyRegistryServiceTest {

  @Mock private PropertyDefinitionRepository repository;

  private PropertyRegistryService service;
  private PropertyDefinitionValidator validator;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    PolicyRegistry policies =
        new PolicyRegistry(
            List.of(TwistV1.INSTANCE, LinearDensityV1.INSTANCE),
            List.of(Tex2dp.INSTANCE, Tpm2dp.INSTANCE));
    validator = new PropertyDefinitionValidator(policies);
    service = new PropertyRegistryService(repository, validator, policies);
    tenantId = UUID.randomUUID();
  }

  @Test
  void resolveBindsCodeOwnedPoliciesUsingExplicitTenantQuery() {
    PropertyDefinitionSpec spec = system("YARN_TWIST_TPM");
    PropertyDefinition definition = stored(spec);
    when(repository.findByTenantIdAndPropertyKey(tenantId, spec.propertyKey()))
        .thenReturn(Optional.of(definition));

    ResolvedPropertyDefinition resolved = service.resolve(tenantId, spec.propertyKey());

    assertThat(resolved.definition()).isSameAs(definition);
    assertThat(resolved.conversionPolicy()).contains(TwistV1.INSTANCE);
    assertThat(resolved.roundingPolicy()).contains(Tpm2dp.INSTANCE);
    verify(repository).findByTenantIdAndPropertyKey(tenantId, spec.propertyKey());
  }

  @Test
  void toCanonicalUsesConversionThenRounding() {
    PropertyDefinitionSpec spec = system("YARN_TWIST_TPM");
    when(repository.findByTenantIdAndPropertyKey(tenantId, spec.propertyKey()))
        .thenReturn(Optional.of(stored(spec)));

    assertThat(
            service.toCanonical(tenantId, spec.propertyKey(), new BigDecimal("18.5"), UnitCode.TPI))
        .isEqualByComparingTo("728.35");
  }

  @Test
  void rule11RejectsStructurallyValidSystemCanonicalDrift() {
    PropertyDefinitionSpec expected = system("YARN_TWIST_TPM");
    PropertyDefinitionSpec drift =
        new PropertyDefinitionSpec(
            expected.propertyKey(),
            expected.canonicalFieldName(),
            expected.semanticRoleDefault(),
            expected.dimension(),
            expected.dataType(),
            expected.unitFamily(),
            UnitCode.TPI,
            expected.allowedUnitCodes(),
            expected.conversionPolicy(),
            expected.roundingPolicy(),
            expected.nominalSource(),
            expected.toleranceSource(),
            expected.description(),
            true);
    validator.validate(drift);
    when(repository.findByTenantIdAndPropertyKey(tenantId, drift.propertyKey()))
        .thenReturn(Optional.of(stored(drift)));

    assertThatThrownBy(() -> service.resolve(tenantId, drift.propertyKey()))
        .isInstanceOf(PropertyRegistryException.class)
        .hasMessageContaining("drift")
        .hasMessageContaining(drift.propertyKey());
  }

  @Test
  void tenantExtensionsRequireCustomNamespaceAndDoNotBecomeSystemRows() {
    DefineTenantPropertyCommand command =
        new DefineTenantPropertyCommand(
            "CUSTOM_DEVICE_READING",
            "deviceReading",
            SemanticRole.MEASUREMENT,
            "deviceReading",
            PropertyDataType.DECIMAL,
            UnitFamily.NONE,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            "Tenant-owned device reading.");
    when(repository.existsByTenantIdAndPropertyKey(tenantId, command.propertyKey()))
        .thenReturn(false);
    when(repository.save(org.mockito.ArgumentMatchers.any(PropertyDefinition.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ResolvedPropertyDefinition resolved = service.defineTenantProperty(tenantId, command);

    assertThat(resolved.definition().getTenantId()).isEqualTo(tenantId);
    assertThat(resolved.definition().isSystemDefined()).isFalse();
    assertThat(resolved.definition().getNominalSource()).isNull();
    assertThat(resolved.definition().getToleranceSource()).isNull();
  }

  @Test
  void duplicateTenantKeyIsRejectedBeforeInsert() {
    DefineTenantPropertyCommand command =
        new DefineTenantPropertyCommand(
            "CUSTOM_DUPLICATE",
            "duplicate",
            SemanticRole.MEASUREMENT,
            "duplicate",
            PropertyDataType.DECIMAL,
            UnitFamily.NONE,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            "Duplicate test.");
    when(repository.existsByTenantIdAndPropertyKey(tenantId, command.propertyKey()))
        .thenReturn(true);

    assertThatThrownBy(() -> service.defineTenantProperty(tenantId, command))
        .isInstanceOf(PropertyRegistryException.class)
        .hasMessageContaining("already exists");
  }

  private PropertyDefinition stored(PropertyDefinitionSpec spec) {
    PropertyDefinition definition = PropertyDefinition.from(spec);
    definition.setTenantId(tenantId);
    return definition;
  }

  private static PropertyDefinitionSpec system(String key) {
    return PropertyDefinitionSeeder.systemDefinitions().stream()
        .filter(definition -> definition.propertyKey().equals(key))
        .findFirst()
        .orElseThrow();
  }
}
