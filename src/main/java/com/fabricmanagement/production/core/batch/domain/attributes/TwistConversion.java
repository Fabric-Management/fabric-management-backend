package com.fabricmanagement.production.core.batch.domain.attributes;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Canonical twist-density conversion policy for yarn batch attributes. */
public final class TwistConversion {

  public static final String POLICY_NAME = "twist-v1";
  public static final BigDecimal TPI_TO_TPM_FACTOR = new BigDecimal("39.37");
  public static final int OUTPUT_SCALE = 2;
  public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  private TwistConversion() {
    // Utility class — prevent instantiation
  }

  /** Converts turns per inch to canonical turns per metre using {@value #POLICY_NAME}. */
  public static BigDecimal tpiToTpm(BigDecimal turnsPerInch) {
    if (turnsPerInch == null) {
      return null;
    }
    return turnsPerInch.multiply(TPI_TO_TPM_FACTOR).setScale(OUTPUT_SCALE, ROUNDING_MODE);
  }
}
