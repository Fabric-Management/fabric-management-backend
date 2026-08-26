package com.fabricmanagement.product.core.domain.registry;

import java.util.List;

public record PropertyDefinitionSpec(
    String propertyKey,
    String canonicalFieldName,
    SemanticRole semanticRoleDefault,
    String dimension,
    PropertyDataType dataType,
    UnitFamily unitFamily,
    UnitCode canonicalUnitCode,
    List<UnitCode> allowedUnitCodes,
    String conversionPolicy,
    String roundingPolicy,
    String nominalSource,
    String toleranceSource,
    String description,
    boolean systemDefined) {

  public PropertyDefinitionSpec {
    allowedUnitCodes = allowedUnitCodes == null ? List.of() : List.copyOf(allowedUnitCodes);
  }
}
