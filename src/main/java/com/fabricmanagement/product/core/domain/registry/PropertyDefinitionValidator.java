package com.fabricmanagement.product.core.domain.registry;

import com.fabricmanagement.product.core.domain.registry.policy.ConversionPolicy;
import com.fabricmanagement.product.core.domain.registry.policy.PolicyRegistry;
import com.fabricmanagement.product.core.domain.registry.policy.ResolverPolicyNames;
import com.fabricmanagement.product.core.domain.registry.policy.RoundingPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PropertyDefinitionValidator {

  private final PolicyRegistry policyRegistry;

  public PropertyDefinitionValidator(PolicyRegistry policyRegistry) {
    this.policyRegistry = policyRegistry;
  }

  public void validate(PropertyDefinition definition) {
    validate(definition.toSpec());
  }

  public void validate(PropertyDefinitionSpec definition) {
    String key = definition.propertyKey();
    ConversionPolicy conversion =
        definition.conversionPolicy() == null
            ? null
            : policyRegistry
                .conversion(definition.conversionPolicy())
                .orElseThrow(() -> failure(key, 1, "unknown conversion policy"));
    RoundingPolicy rounding =
        definition.roundingPolicy() == null
            ? null
            : policyRegistry
                .rounding(definition.roundingPolicy())
                .orElseThrow(() -> failure(key, 1, "unknown rounding policy"));

    List<UnitCode> allowed = definition.allowedUnitCodes();
    if (definition.canonicalUnitCode() != null
        && !allowed.contains(definition.canonicalUnitCode())) {
      throw failure(key, 3, "canonical unit is not allowed");
    }
    if (definition.unitFamily() == UnitFamily.NONE
        && (definition.canonicalUnitCode() != null || !allowed.isEmpty())) {
      throw failure(key, 8, "NONE unit family cannot declare units");
    }
    if (allowed.stream().anyMatch(code -> code.unitFamily() != definition.unitFamily())) {
      throw failure(key, 4, "allowed unit belongs to another family");
    }
    if (conversion != null && conversion.unitFamily() != definition.unitFamily()) {
      throw failure(key, 5, "conversion policy belongs to another family");
    }
    if (rounding != null && rounding.unitFamily() != definition.unitFamily()) {
      throw failure(key, 6, "rounding policy belongs to another family");
    }
    if (allowed.size() > 1 && conversion == null) {
      throw failure(key, 7, "multiple allowed units require a conversion policy");
    }
    if (definition.unitFamily() != UnitFamily.NONE
        && definition.dataType() != PropertyDataType.DECIMAL
        && definition.dataType() != PropertyDataType.INTEGER) {
      throw failure(key, 8, "unit-bearing properties must be numeric");
    }

    if (new HashSet<>(allowed).size() != allowed.size()) {
      throw failure(key, 9, "allowed units contain duplicates");
    }
    boolean customPrefix = key != null && key.startsWith("CUSTOM_");
    if (definition.systemDefined() == customPrefix) {
      throw failure(key, 9, "CUSTOM_ namespace does not match system ownership");
    }
    validateResolver(key, definition.nominalSource());
    validateResolver(key, definition.toleranceSource());
    if (definition.systemDefined()
        && (definition.nominalSource() == null || definition.toleranceSource() == null)) {
      throw failure(key, 10, "system rows require nominal and tolerance resolver names");
    }
    if (definition.description() == null || definition.description().isBlank()) {
      throw new PropertyRegistryException(
          "Property definition " + key + " violates R1: description is required");
    }
  }

  public void validateUniqueKeys(List<PropertyDefinitionSpec> definitions) {
    Set<String> keys = new HashSet<>();
    for (PropertyDefinitionSpec definition : definitions) {
      if (!keys.add(definition.propertyKey())) {
        throw failure(definition.propertyKey(), 2, "duplicate property key");
      }
      validate(definition);
    }
  }

  private static void validateResolver(String key, String resolver) {
    if (!ResolverPolicyNames.isKnown(resolver)) {
      throw failure(key, 10, "unknown resolver name: " + resolver);
    }
  }

  private static PropertyRegistryException failure(String key, int rule, String detail) {
    return new PropertyRegistryException(
        "Property definition " + key + " violates R4 rule " + rule + ": " + detail);
  }
}
