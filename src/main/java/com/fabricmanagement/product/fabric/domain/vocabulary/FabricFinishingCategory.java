package com.fabricmanagement.product.fabric.domain.vocabulary;

/**
 * The closed kinds of wet and dry processing a fabric lot may go through (FABRIC-ARCHITECTURE F7,
 * F10). A tenant's own finishing process maps to one of them, as a construction maps to a family.
 * What a lot has become — dyed, printed, coated — is read from the categories of the processes
 * recorded on it.
 */
public enum FabricFinishingCategory {
  /** Singeing, desizing, scouring, bleaching, mercerising, heat setting. */
  PRETREATMENT,
  /** The lot takes its colour. */
  DYEING,
  /** A pattern or colour printed onto the lot. */
  PRINTING,
  /** Softening, water repellency, easy care, flame retardancy and similar chemical finishes. */
  CHEMICAL_FINISH,
  /** Sanforising, raising, peaching, calendering, compacting, stentering. */
  MECHANICAL_FINISH,
  /** Coating or laminating: a layer that the fabric's label must declare. */
  COATING;

  /** Whether a lot that went through this process has a colour of its own. */
  public boolean colours() {
    return this == DYEING || this == PRINTING;
  }

  /** Whether the process adds a layer that changes what the label declares. */
  public boolean affectsLabel() {
    return this == COATING;
  }
}
