package com.fabricmanagement.product.yarn.domain.vocabulary;

import com.fabricmanagement.product.core.domain.registry.UnitCode;

/**
 * Yarn count systems supported by the v1 unit contract.
 *
 * <p>NW (worsted) and NeL (linen) stay out until a concrete requirement ratifies their conversion
 * constants. Conversion is delegated to the Property Registry's {@code linearDensity-v1} policy;
 * this enum does not perform yarn-local arithmetic.
 */
public enum CountSystem {
  TEX(UnitCode.TEX),
  DTEX(UnitCode.DTEX),
  DENIER(UnitCode.DENIER),
  NE(UnitCode.NE),
  NM(UnitCode.NM);

  private final UnitCode unitCode;

  CountSystem(UnitCode unitCode) {
    this.unitCode = unitCode;
  }

  public UnitCode unitCode() {
    return unitCode;
  }
}
