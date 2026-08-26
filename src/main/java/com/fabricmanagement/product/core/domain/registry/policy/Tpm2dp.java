package com.fabricmanagement.product.core.domain.registry.policy;

import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Tpm2dp implements RoundingPolicy {

  public static final Tpm2dp INSTANCE = new Tpm2dp();
  public static final String POLICY_NAME = "tpm-2dp";
  public static final int OUTPUT_SCALE = 2;
  public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  private Tpm2dp() {}

  @Override
  public String name() {
    return POLICY_NAME;
  }

  @Override
  public UnitFamily unitFamily() {
    return UnitFamily.TWIST_DENSITY;
  }

  @Override
  public BigDecimal apply(BigDecimal value) {
    return value == null ? null : value.setScale(OUTPUT_SCALE, ROUNDING_MODE);
  }
}
