package com.fabricmanagement.product.fabric.domain.vocabulary;

/**
 * Closed construction families (FABRIC-ARCHITECTURE F3). A tenant's own construction names ("3/1 S
 * twill", "Lacoste pique") each map to exactly one family, so the system can reason about them; a
 * new family is a release, not data.
 */
public enum FabricConstructionFamily {
  PLAIN(FabricFormation.WOVEN),
  TWILL(FabricFormation.WOVEN),
  SATIN(FabricFormation.WOVEN),
  DOBBY(FabricFormation.WOVEN),
  JACQUARD(FabricFormation.WOVEN),
  LENO(FabricFormation.WOVEN),
  PILE(FabricFormation.WOVEN),
  SINGLE_JERSEY(FabricFormation.KNITTED),
  RIB(FabricFormation.KNITTED),
  INTERLOCK(FabricFormation.KNITTED),
  PIQUE(FabricFormation.KNITTED),
  FLEECE(FabricFormation.KNITTED),
  TERRY(FabricFormation.KNITTED),
  WARP_KNIT(FabricFormation.KNITTED),
  NONWOVEN(FabricFormation.NONWOVEN);

  private final FabricFormation formation;

  FabricConstructionFamily(FabricFormation formation) {
    this.formation = formation;
  }

  public FabricFormation formation() {
    return formation;
  }
}
