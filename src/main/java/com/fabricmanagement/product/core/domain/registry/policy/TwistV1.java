package com.fabricmanagement.product.core.domain.registry.policy;

import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import com.fabricmanagement.product.core.domain.registry.UnitCode;
import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import java.math.BigDecimal;
import java.math.MathContext;

public final class TwistV1 implements ConversionPolicy {

  public static final TwistV1 INSTANCE = new TwistV1();
  public static final String POLICY_NAME = "twist-v1";
  public static final BigDecimal TPI_TO_TPM_FACTOR = new BigDecimal("39.37");

  private TwistV1() {}

  @Override
  public String name() {
    return POLICY_NAME;
  }

  @Override
  public UnitFamily unitFamily() {
    return UnitFamily.TWIST_DENSITY;
  }

  @Override
  public BigDecimal toCanonical(BigDecimal value, UnitCode sourceUnit) {
    if (value == null) {
      return null;
    }
    rejectNegative(value);
    if (sourceUnit == null) {
      throw unsupported(null);
    }
    return switch (sourceUnit) {
      case TPM -> value;
      case TPI -> value.multiply(TPI_TO_TPM_FACTOR, MathContext.DECIMAL64);
      default -> throw unsupported(sourceUnit);
    };
  }

  @Override
  public BigDecimal fromCanonical(BigDecimal value, UnitCode targetUnit) {
    if (value == null) {
      return null;
    }
    rejectNegative(value);
    if (targetUnit == null) {
      throw unsupported(null);
    }
    return switch (targetUnit) {
      case TPM -> value;
      case TPI -> value.divide(TPI_TO_TPM_FACTOR, MathContext.DECIMAL64);
      default -> throw unsupported(targetUnit);
    };
  }

  private static void rejectNegative(BigDecimal value) {
    if (value.signum() < 0) {
      throw new PropertyRegistryException("twist-v1 rejects negative twist density: " + value);
    }
  }

  private static PropertyRegistryException unsupported(UnitCode unitCode) {
    return new PropertyRegistryException("twist-v1 does not support unit: " + unitCode);
  }
}
