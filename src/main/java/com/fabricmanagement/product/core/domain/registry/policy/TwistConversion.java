package com.fabricmanagement.product.core.domain.registry.policy;

import com.fabricmanagement.product.core.domain.registry.UnitCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Compatibility facade for the YARN-0C static conversion surface. */
public final class TwistConversion {

  public static final String POLICY_NAME = TwistV1.POLICY_NAME;
  public static final BigDecimal TPI_TO_TPM_FACTOR = TwistV1.TPI_TO_TPM_FACTOR;
  public static final int OUTPUT_SCALE = Tpm2dp.OUTPUT_SCALE;
  public static final RoundingMode ROUNDING_MODE = Tpm2dp.ROUNDING_MODE;

  private TwistConversion() {}

  public static BigDecimal tpiToTpm(BigDecimal turnsPerInch) {
    return Tpm2dp.INSTANCE.apply(TwistV1.INSTANCE.toCanonical(turnsPerInch, UnitCode.TPI));
  }
}
