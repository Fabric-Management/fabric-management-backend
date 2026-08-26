package com.fabricmanagement.product.core.domain.registry;

public enum UnitCode {
  MM(UnitFamily.LENGTH),
  CM(UnitFamily.LENGTH),
  M(UnitFamily.LENGTH),
  G(UnitFamily.WEIGHT),
  KG(UnitFamily.WEIGHT),
  PCT(UnitFamily.PERCENTAGE),
  EACH(UnitFamily.COUNT),
  TEX(UnitFamily.LINEAR_DENSITY),
  DTEX(UnitFamily.LINEAR_DENSITY),
  DENIER(UnitFamily.LINEAR_DENSITY),
  NE(UnitFamily.LINEAR_DENSITY),
  NM(UnitFamily.LINEAR_DENSITY),
  TPM(UnitFamily.TWIST_DENSITY),
  TPI(UnitFamily.TWIST_DENSITY),
  CN_PER_TEX(UnitFamily.TENACITY),
  RKM(UnitFamily.TENACITY);

  private final UnitFamily unitFamily;

  UnitCode(UnitFamily unitFamily) {
    this.unitFamily = unitFamily;
  }

  public UnitFamily unitFamily() {
    return unitFamily;
  }
}
