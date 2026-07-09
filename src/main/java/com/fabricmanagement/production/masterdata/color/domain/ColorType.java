package com.fabricmanagement.production.masterdata.color.domain;

/**
 * What kind of colour a card represents.
 *
 * <p>{@link #PFD} and {@link #GREIGE} are <em>undyed</em>: they carry no shade standard, so hex,
 * Pantone and Lab values must be absent. See {@link #isUndyed()}.
 */
public enum ColorType {
  /** Piece-dyed or solution-dyed to a target shade. */
  DYED,
  /** Woven or knitted from pre-dyed yarn. */
  YARN_DYED,
  /** Multi-colour print; the card is the print identity, not its individual colours. */
  PRINTED,
  /** White achieved with optical brightening agents. Distinct from undyed white. */
  OPTICAL_WHITE,
  /** Prepared for dyeing: scoured/bleached, awaiting colour. */
  PFD,
  /** Loom-state, unprocessed. */
  GREIGE;

  public boolean isUndyed() {
    return this == PFD || this == GREIGE;
  }
}
