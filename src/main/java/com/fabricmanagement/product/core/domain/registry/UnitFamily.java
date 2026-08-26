package com.fabricmanagement.product.core.domain.registry;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum UnitFamily {
  LENGTH,
  WEIGHT,
  PERCENTAGE,
  COUNT,
  NONE,
  LINEAR_DENSITY,
  TWIST_DENSITY,
  TENACITY;

  public Set<UnitCode> codes() {
    return Arrays.stream(UnitCode.values())
        .filter(code -> code.unitFamily() == this)
        .collect(Collectors.toUnmodifiableSet());
  }
}
