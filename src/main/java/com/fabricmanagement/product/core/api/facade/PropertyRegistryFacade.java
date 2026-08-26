package com.fabricmanagement.product.core.api.facade;

import com.fabricmanagement.product.core.app.ResolvedPropertyDefinition;
import com.fabricmanagement.product.core.domain.registry.UnitCode;
import java.math.BigDecimal;
import java.util.UUID;

public interface PropertyRegistryFacade {

  ResolvedPropertyDefinition resolve(UUID tenantId, String propertyKey);

  BigDecimal toCanonical(UUID tenantId, String propertyKey, BigDecimal value, UnitCode sourceUnit);

  BigDecimal fromCanonical(
      UUID tenantId, String propertyKey, BigDecimal value, UnitCode targetUnit);
}
