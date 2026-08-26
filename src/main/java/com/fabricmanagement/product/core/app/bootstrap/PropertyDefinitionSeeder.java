package com.fabricmanagement.product.core.app.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.registry.PropertyDataType;
import com.fabricmanagement.product.core.domain.registry.PropertyDefinition;
import com.fabricmanagement.product.core.domain.registry.PropertyDefinitionSpec;
import com.fabricmanagement.product.core.domain.registry.PropertyDefinitionValidator;
import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import com.fabricmanagement.product.core.domain.registry.SemanticRole;
import com.fabricmanagement.product.core.domain.registry.UnitCode;
import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import com.fabricmanagement.product.core.domain.registry.policy.LinearDensityV1;
import com.fabricmanagement.product.core.domain.registry.policy.ResolverPolicyNames;
import com.fabricmanagement.product.core.domain.registry.policy.Tex2dp;
import com.fabricmanagement.product.core.domain.registry.policy.Tpm2dp;
import com.fabricmanagement.product.core.domain.registry.policy.TwistV1;
import com.fabricmanagement.product.core.infra.repository.PropertyDefinitionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class PropertyDefinitionSeeder {

  private static final List<PropertyDefinitionSpec> SYSTEM_DEFINITIONS =
      List.of(
          system(
              "YARN_RESULTANT_LINEAR_DENSITY",
              "resultantLinearDensityTex",
              "linearDensity",
              UnitFamily.LINEAR_DENSITY,
              UnitCode.TEX,
              List.of(UnitCode.TEX, UnitCode.DTEX, UnitCode.DENIER, UnitCode.NE, UnitCode.NM),
              LinearDensityV1.POLICY_NAME,
              Tex2dp.POLICY_NAME,
              ResolverPolicyNames.COMMERCIAL_CONFORMANCE_TOLERANCE,
              "Resultant linear density of the yarn as a whole, stored in tex."),
          system(
              "YARN_TWIST_TPM",
              "turnsPerMeter",
              "twistDensity",
              UnitFamily.TWIST_DENSITY,
              UnitCode.TPM,
              List.of(UnitCode.TPM, UnitCode.TPI),
              TwistV1.POLICY_NAME,
              Tpm2dp.POLICY_NAME,
              ResolverPolicyNames.COMMERCIAL_CONFORMANCE_TOLERANCE,
              "Twist density of the yarn, stored in turns per metre."),
          system(
              "YARN_CSP",
              "csp",
              "strength",
              UnitFamily.NONE,
              null,
              List.of(),
              null,
              null,
              ResolverPolicyNames.QUALITY_ACCEPTANCE_PROFILE,
              "Count strength product; a unitless composite of count and lea strength."),
          system(
              "YARN_TENACITY",
              "tenacityCnPerTex",
              "strength",
              UnitFamily.TENACITY,
              UnitCode.CN_PER_TEX,
              List.of(UnitCode.CN_PER_TEX),
              null,
              null,
              ResolverPolicyNames.QUALITY_ACCEPTANCE_PROFILE,
              "Breaking tenacity of the yarn, stored in centinewtons per tex."),
          system(
              "YARN_ELONGATION",
              "elongationPct",
              "elongation",
              UnitFamily.PERCENTAGE,
              UnitCode.PCT,
              List.of(UnitCode.PCT),
              null,
              null,
              ResolverPolicyNames.QUALITY_ACCEPTANCE_PROFILE,
              "Elongation at break, as a percentage."),
          system(
              "YARN_EVENNESS_CVM",
              "evennessCvmPct",
              "evenness",
              UnitFamily.PERCENTAGE,
              UnitCode.PCT,
              List.of(UnitCode.PCT),
              null,
              null,
              ResolverPolicyNames.QUALITY_ACCEPTANCE_PROFILE,
              "Mass evenness expressed as coefficient of variation (CVm), as a percentage."));

  private final PropertyDefinitionRepository repository;
  private final PropertyDefinitionValidator validator;
  private final TransactionTemplate transactionTemplate;

  public static List<PropertyDefinitionSpec> systemDefinitions() {
    return SYSTEM_DEFINITIONS;
  }

  public int seed(UUID tenantId) {
    validator.validateUniqueKeys(SYSTEM_DEFINITIONS);
    return TenantContext.executeInTenantContext(
        tenantId,
        () ->
            transactionTemplate.execute(
                status -> {
                  Map<String, PropertyDefinition> existing =
                      repository.findByTenantId(tenantId).stream()
                          .collect(
                              Collectors.toMap(
                                  PropertyDefinition::getPropertyKey, Function.identity()));
                  List<PropertyDefinition> missing =
                      SYSTEM_DEFINITIONS.stream()
                          .filter(spec -> !existing.containsKey(spec.propertyKey()))
                          .map(PropertyDefinition::from)
                          .peek(definition -> definition.setTenantId(tenantId))
                          .toList();
                  repository.saveAll(missing);
                  log.info(
                      "Seeded {} missing property definitions for tenant {}.",
                      missing.size(),
                      tenantId);
                  return missing.size();
                }));
  }

  public void validateSystemRows(UUID tenantId) {
    TenantContext.executeInTenantContext(
        tenantId, () -> validateSystemRowsInCurrentTenant(tenantId));
  }

  private void validateSystemRowsInCurrentTenant(UUID tenantId) {
    Map<String, PropertyDefinitionSpec> expected =
        SYSTEM_DEFINITIONS.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    PropertyDefinitionSpec::propertyKey, Function.identity()));
    List<PropertyDefinition> stored = repository.findByTenantIdAndSystemDefinedTrue(tenantId);
    for (PropertyDefinition definition : stored) {
      validator.validate(definition);
      PropertyDefinitionSpec desired = expected.get(definition.getPropertyKey());
      if (desired == null || !definition.contractEquals(desired)) {
        throw new PropertyRegistryException(
            "Property definition "
                + definition.getPropertyKey()
                + " violates R4 rule 11: system contract drift");
      }
    }
    for (PropertyDefinitionSpec desired : SYSTEM_DEFINITIONS) {
      if (stored.stream().noneMatch(row -> row.getPropertyKey().equals(desired.propertyKey()))) {
        throw new PropertyRegistryException(
            "Property definition "
                + desired.propertyKey()
                + " violates R4 rule 11: system catalogue row is missing");
      }
    }
  }

  private static PropertyDefinitionSpec system(
      String key,
      String field,
      String dimension,
      UnitFamily family,
      UnitCode canonical,
      List<UnitCode> allowed,
      String conversion,
      String rounding,
      String tolerance,
      String description) {
    return new PropertyDefinitionSpec(
        key,
        field,
        SemanticRole.MEASUREMENT,
        dimension,
        PropertyDataType.DECIMAL,
        family,
        canonical,
        allowed,
        conversion,
        rounding,
        ResolverPolicyNames.YARN_LOT_NOMINAL_V1,
        tolerance,
        description,
        true);
  }
}
