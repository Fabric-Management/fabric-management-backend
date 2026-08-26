package com.fabricmanagement.product.core.domain.registry.policy;

import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Tex2dp implements RoundingPolicy {

  public static final Tex2dp INSTANCE = new Tex2dp();
  public static final String POLICY_NAME = "tex-2dp";
  public static final int OUTPUT_SCALE = 2;
  public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  private Tex2dp() {}

  @Override
  public String name() {
    return POLICY_NAME;
  }

  @Override
  public UnitFamily unitFamily() {
    return UnitFamily.LINEAR_DENSITY;
  }

  @Override
  public BigDecimal apply(BigDecimal value) {
    return value == null ? null : value.setScale(OUTPUT_SCALE, ROUNDING_MODE);
  }
}
