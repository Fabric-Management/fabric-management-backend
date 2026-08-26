package com.fabricmanagement.product.core.domain.registry.policy;

import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import java.math.BigDecimal;

public interface RoundingPolicy {
  String name();

  UnitFamily unitFamily();

  BigDecimal apply(BigDecimal value);
}
