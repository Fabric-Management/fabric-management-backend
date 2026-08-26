package com.fabricmanagement.product.core.app;

import com.fabricmanagement.product.core.domain.registry.PropertyDataType;
import com.fabricmanagement.product.core.domain.registry.SemanticRole;
import com.fabricmanagement.product.core.domain.registry.UnitCode;
import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import java.util.List;

public record DefineTenantPropertyCommand(
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
    String description) {}
