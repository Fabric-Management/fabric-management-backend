package com.fabricmanagement.product.core.domain.registry.policy;

import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import com.fabricmanagement.product.core.domain.registry.UnitCode;
import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import java.math.BigDecimal;
import java.math.MathContext;

public final class LinearDensityV1 implements ConversionPolicy {

  public static final LinearDensityV1 INSTANCE = new LinearDensityV1();
  public static final String POLICY_NAME = "linearDensity-v1";
  private static final BigDecimal NE_FACTOR = new BigDecimal("590.5");
  private static final BigDecimal NM_FACTOR = new BigDecimal("1000");
  private static final BigDecimal DENIER_FACTOR = new BigDecimal("9");
  private static final BigDecimal DTEX_FACTOR = new BigDecimal("10");

  private LinearDensityV1() {}

  @Override
  public String name() {
    return POLICY_NAME;
  }

  @Override
  public UnitFamily unitFamily() {
    return UnitFamily.LINEAR_DENSITY;
  }

  @Override
  public BigDecimal toCanonical(BigDecimal value, UnitCode sourceUnit) {
    if (value == null) {
      return null;
    }
    rejectNonPositive(value);
    if (sourceUnit == null) {
      throw unsupported(null);
    }
    return switch (sourceUnit) {
      case TEX -> value;
      case DTEX -> value.divide(DTEX_FACTOR, MathContext.DECIMAL64);
      case DENIER -> value.divide(DENIER_FACTOR, MathContext.DECIMAL64);
      case NE -> NE_FACTOR.divide(value, MathContext.DECIMAL64);
      case NM -> NM_FACTOR.divide(value, MathContext.DECIMAL64);
      default -> throw unsupported(sourceUnit);
    };
  }

  @Override
  public BigDecimal fromCanonical(BigDecimal value, UnitCode targetUnit) {
    if (value == null) {
      return null;
    }
    rejectNonPositive(value);
    if (targetUnit == null) {
      throw unsupported(null);
    }
    return switch (targetUnit) {
      case TEX -> value;
      case DTEX -> value.multiply(DTEX_FACTOR, MathContext.DECIMAL64);
      case DENIER -> value.multiply(DENIER_FACTOR, MathContext.DECIMAL64);
      case NE -> NE_FACTOR.divide(value, MathContext.DECIMAL64);
      case NM -> NM_FACTOR.divide(value, MathContext.DECIMAL64);
      default -> throw unsupported(targetUnit);
    };
  }

  private static void rejectNonPositive(BigDecimal value) {
    if (value.signum() <= 0) {
      throw new PropertyRegistryException("linearDensity-v1 requires a positive value: " + value);
    }
  }

  private static PropertyRegistryException unsupported(UnitCode unitCode) {
    return new PropertyRegistryException("linearDensity-v1 does not support unit: " + unitCode);
  }
}
