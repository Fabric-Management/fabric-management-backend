package com.fabricmanagement.product.fabric.domain.vocabulary;

import java.util.EnumSet;
import java.util.Set;

/** What a yarn does in the fabric (FABRIC-ARCHITECTURE F4). A nonwoven has no yarns. */
public enum FabricYarnRole {
  WARP(EnumSet.of(FabricFormation.WOVEN)),
  WEFT(EnumSet.of(FabricFormation.WOVEN)),
  KNIT_FEED(EnumSet.of(FabricFormation.KNITTED)),
  PILE(EnumSet.of(FabricFormation.WOVEN, FabricFormation.KNITTED)),
  GROUND(EnumSet.of(FabricFormation.WOVEN, FabricFormation.KNITTED));

  private final Set<FabricFormation> formations;

  FabricYarnRole(Set<FabricFormation> formations) {
    this.formations = formations;
  }

  public boolean fits(FabricFormation formation) {
    return formations.contains(formation);
  }
}
