package com.fabricmanagement.product.core.domain.registry.policy;

import com.fabricmanagement.product.core.domain.registry.UnitCode;
import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import java.math.BigDecimal;

public interface ConversionPolicy {
  String name();

  UnitFamily unitFamily();

  BigDecimal toCanonical(BigDecimal value, UnitCode sourceUnit);

  BigDecimal fromCanonical(BigDecimal value, UnitCode targetUnit);
}
