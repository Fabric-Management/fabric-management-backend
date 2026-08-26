package com.fabricmanagement.product.core.app;

import com.fabricmanagement.product.core.api.facade.PropertyRegistryFacade;
import com.fabricmanagement.product.core.app.bootstrap.PropertyDefinitionSeeder;
import com.fabricmanagement.product.core.domain.registry.PropertyDefinition;
import com.fabricmanagement.product.core.domain.registry.PropertyDefinitionSpec;
import com.fabricmanagement.product.core.domain.registry.PropertyDefinitionValidator;
import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import com.fabricmanagement.product.core.domain.registry.UnitCode;
import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import com.fabricmanagement.product.core.domain.registry.policy.ConversionPolicy;
import com.fabricmanagement.product.core.domain.registry.policy.PolicyRegistry;
import com.fabricmanagement.product.core.domain.registry.policy.RoundingPolicy;
import com.fabricmanagement.product.core.infra.repository.PropertyDefinitionRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PropertyRegistryService implements PropertyRegistryFacade {

  private final PropertyDefinitionRepository repository;
  private final PropertyDefinitionValidator validator;
  private final PolicyRegistry policyRegistry;

  @Override
  @Transactional(readOnly = true)
  public ResolvedPropertyDefinition resolve(UUID tenantId, String propertyKey) {
    PropertyDefinition definition =
        repository
            .findByTenantIdAndPropertyKey(tenantId, propertyKey)
            .orElseThrow(
                () ->
                    new PropertyRegistryException(
                        "Property definition not found: tenant="
                            + tenantId
                            + ", key="
                            + propertyKey));
    validator.validate(definition);
    if (definition.isSystemDefined()) {
      assertSystemContract(definition);
    }
    return bind(definition);
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal toCanonical(
      UUID tenantId, String propertyKey, BigDecimal value, UnitCode sourceUnit) {
    ResolvedPropertyDefinition resolved = resolve(tenantId, propertyKey);
    assertAllowed(resolved.definition(), sourceUnit);
    BigDecimal canonical =
        resolved
            .conversionPolicy()
            .map(policy -> policy.toCanonical(value, sourceUnit))
            .orElseGet(() -> identityOnly(resolved.definition(), value, sourceUnit, "source"));
    return resolved.roundingPolicy().map(policy -> policy.apply(canonical)).orElse(canonical);
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal fromCanonical(
      UUID tenantId, String propertyKey, BigDecimal value, UnitCode targetUnit) {
    ResolvedPropertyDefinition resolved = resolve(tenantId, propertyKey);
    assertAllowed(resolved.definition(), targetUnit);
    BigDecimal converted =
        resolved
            .conversionPolicy()
            .map(policy -> policy.fromCanonical(value, targetUnit))
            .orElseGet(() -> identityOnly(resolved.definition(), value, targetUnit, "target"));
    return resolved.roundingPolicy().map(policy -> policy.apply(converted)).orElse(converted);
  }

  @Transactional
  public ResolvedPropertyDefinition defineTenantProperty(
      UUID tenantId, DefineTenantPropertyCommand command) {
    PropertyDefinitionSpec spec =
        new PropertyDefinitionSpec(
            command.propertyKey(),
            command.canonicalFieldName(),
            command.semanticRoleDefault(),
            command.dimension(),
            command.dataType(),
            command.unitFamily(),
            command.canonicalUnitCode(),
            command.allowedUnitCodes(),
            command.conversionPolicy(),
            command.roundingPolicy(),
            command.nominalSource(),
            command.toleranceSource(),
            command.description(),
            false);
    validator.validate(spec);
    if (repository.existsByTenantIdAndPropertyKey(tenantId, command.propertyKey())) {
      throw new PropertyRegistryException("Property key already exists: " + command.propertyKey());
    }
    PropertyDefinition definition = PropertyDefinition.from(spec);
    definition.setTenantId(tenantId);
    return bind(repository.save(definition));
  }

  private ResolvedPropertyDefinition bind(PropertyDefinition definition) {
    Optional<ConversionPolicy> conversion =
        policyRegistry.conversion(definition.getConversionPolicy());
    Optional<RoundingPolicy> rounding = policyRegistry.rounding(definition.getRoundingPolicy());
    return new ResolvedPropertyDefinition(definition, conversion, rounding);
  }

  private static void assertAllowed(PropertyDefinition definition, UnitCode unitCode) {
    if (definition.getUnitFamily() == UnitFamily.NONE && unitCode == null) {
      return;
    }
    if (!definition.getAllowedUnitCodes().contains(unitCode)) {
      throw new PropertyRegistryException(
          "Property " + definition.getPropertyKey() + " does not allow unit " + unitCode);
    }
  }

  private static BigDecimal identityOnly(
      PropertyDefinition definition, BigDecimal value, UnitCode unitCode, String direction) {
    if (definition.getCanonicalUnitCode() != unitCode) {
      throw new PropertyRegistryException(
          "Property "
              + definition.getPropertyKey()
              + " has no conversion policy for "
              + direction
              + " unit "
              + unitCode);
    }
    return value;
  }

  private static void assertSystemContract(PropertyDefinition definition) {
    Map<String, PropertyDefinitionSpec> catalogue =
        PropertyDefinitionSeeder.systemDefinitions().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    PropertyDefinitionSpec::propertyKey, Function.identity()));
    PropertyDefinitionSpec expected = catalogue.get(definition.getPropertyKey());
    if (expected == null || !definition.contractEquals(expected)) {
      throw new PropertyRegistryException(
          "Property definition "
              + definition.getPropertyKey()
              + " violates R4 rule 11: system contract drift");
    }
  }
}
